import boto3
import json
import time
import yaml
from pathlib import Path
from typing import Dict, List, Optional
from botocore.exceptions import ClientError
import logging
import hashlib

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class BedrockSetupWithExistingS3:

    def __init__(self, config_path: str = "LLM/config/config.yaml"):
        self.config = self._load_config(config_path)
        self.region = self.config['aws']['region']

        self.bedrock_agent = boto3.client('bedrock-agent', region_name=self.region)
        self.s3_client = boto3.client('s3', region_name=self.region)
        self.iam_client = boto3.client('iam', region_name=self.region)
        # OpenSearch Serverless 클라이언트 추가
        self.aoss_client = boto3.client('opensearchserverless', region_name=self.region)

        self.bucket_name = self.config['s3']['bucket_name']
        self.kb_name = self.config['knowledge_base']['name']
        self.collection_name = self.config['opensearch']['collection_name']

        self.data_dir = Path("LLM/data/processed/knowledge_base")

        self._verify_s3_bucket()

    def _load_config(self, config_path: str) -> Dict:
        with open(config_path, 'r', encoding='utf-8') as f:
            return yaml.safe_load(f)

    def _verify_s3_bucket(self):
        try:
            self.s3_client.head_bucket(Bucket=self.bucket_name)
            logger.info(f"✅ 기존 S3 버킷 확인 완료: {self.bucket_name}")
        except ClientError as e:
            logger.error(f"❌ S3 버킷을 찾을 수 없습니다: {self.bucket_name}")
            logger.error(f"config.yaml에서 올바른 버킷 이름을 설정해주세요.")
            raise e

    def _get_file_hash(self, file_path: Path) -> str:
        """파일의 해시값을 계산"""
        hash_md5 = hashlib.md5()
        with open(file_path, "rb") as f:
            for chunk in iter(lambda: f.read(4096), b""):
                hash_md5.update(chunk)
        return hash_md5.hexdigest()

    def _file_exists_in_s3(self, key: str) -> bool:
        """S3에 파일이 존재하는지 확인"""
        try:
            self.s3_client.head_object(Bucket=self.bucket_name, Key=key)
            return True
        except ClientError:
            return False

    def _files_are_same(self, local_file: Path, s3_key: str) -> bool:
        """로컬 파일과 S3 파일이 같은지 확인 (해시 비교)"""
        try:
            response = self.s3_client.head_object(Bucket=self.bucket_name, Key=s3_key)
            s3_etag = response['ETag'].strip('"')

            local_hash = self._get_file_hash(local_file)

            return local_hash == s3_etag
        except:
            return False

    def upload_documents_to_s3(self) -> bool:
        try:
            uploaded_count = 0
            skipped_count = 0

            if not self.data_dir.exists():
                logger.error(f"❌ 데이터 폴더가 존재하지 않습니다: {self.data_dir}")
                logger.error("먼저 data_preprocessing.py를 실행하세요.")
                return False

            json_files = list(self.data_dir.glob("*.json"))
            if not json_files:
                logger.error(f"❌ 업로드할 JSON 파일이 없습니다: {self.data_dir}")
                return False

            logger.info(f"📤 {len(json_files)}개의 파일을 확인 중...")

            for json_file in json_files:
                key = f"{self.config['s3']['prefix']}{json_file.name}"

                if self._file_exists_in_s3(key) and self._files_are_same(json_file, key):
                    skipped_count += 1
                    if (uploaded_count + skipped_count) % 50 == 0:
                        logger.info(
                            f"📤 진행중... {uploaded_count + skipped_count}/{len(json_files)} (업로드: {uploaded_count}, 스킵: {skipped_count})")
                    continue

                self.s3_client.upload_file(
                    str(json_file),
                    self.bucket_name,
                    key
                )
                uploaded_count += 1

                if (uploaded_count + skipped_count) % 50 == 0:
                    logger.info(
                        f"📤 진행중... {uploaded_count + skipped_count}/{len(json_files)} (업로드: {uploaded_count}, 스킵: {skipped_count})")

            if uploaded_count > 0:
                logger.info(f"✅ S3 업로드 완료: {uploaded_count}개 파일 업로드")
            if skipped_count > 0:
                logger.info(f"⚡ {skipped_count}개 파일은 이미 존재하여 스킵")

            logger.info(f"📍 업로드 위치: s3://{self.bucket_name}/{self.config['s3']['prefix']}")
            return True

        except Exception as e:
            logger.error(f"❌ S3 업로드 실패: {e}")
            return False

    def create_iam_role(self) -> Optional[str]:
        role_name = f"BedrockKnowledgeBaseRole-{self.kb_name}"

        trust_policy = {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Principal": {
                        "Service": "bedrock.amazonaws.com"
                    },
                    "Action": "sts:AssumeRole"
                }
            ]
        }

        policy_document = {
            "Version": "2012-10-17",
            "Statement": [
                {
                    "Effect": "Allow",
                    "Action": [
                        "s3:GetObject",
                        "s3:ListBucket"
                    ],
                    "Resource": [
                        f"arn:aws:s3:::{self.bucket_name}",
                        f"arn:aws:s3:::{self.bucket_name}/*"
                    ]
                },
                {
                    "Effect": "Allow",
                    "Action": [
                        "aoss:APIAccessAll"
                    ],
                    "Resource": f"arn:aws:aoss:{self.region}:*:collection/*"
                },
                {
                    "Effect": "Allow",
                    "Action": [
                        "bedrock:InvokeModel"
                    ],
                    "Resource": f"arn:aws:bedrock:{self.region}::foundation-model/amazon.titan-embed-text-v1"
                }
            ]
        }

        try:
            try:
                response = self.iam_client.get_role(RoleName=role_name)
                role_arn = response['Role']['Arn']
                logger.info(f"📁 기존 IAM 역할 사용: {role_arn}")
                return role_arn
            except ClientError:
                pass

            response = self.iam_client.create_role(
                RoleName=role_name,
                AssumeRolePolicyDocument=json.dumps(trust_policy),
                Description=f"Role for Bedrock Knowledge Base: {self.kb_name}"
            )

            role_arn = response['Role']['Arn']

            self.iam_client.put_role_policy(
                RoleName=role_name,
                PolicyName=f"BedrockKnowledgeBasePolicy-{self.kb_name}",
                PolicyDocument=json.dumps(policy_document)
            )

            logger.info(f"✅ IAM 역할 생성 완료: {role_arn}")

            time.sleep(10)

            return role_arn

        except ClientError as e:
            logger.error(f"❌ IAM 역할 생성 실패: {e}")
            return None

    def _generate_short_name(self, base_name: str, suffix: str, max_length: int = 32) -> str:
        """32자 이하의 짧은 이름 생성"""
        name_hash = hashlib.md5(base_name.encode()).hexdigest()[:8]
        short_name = f"{name_hash}-{suffix}"

        if len(short_name) <= max_length:
            return short_name

        return f"{name_hash[:6]}-{suffix}"

    def create_opensearch_security_policies(self) -> bool:
        """OpenSearch Serverless 보안 정책 생성"""
        try:
            encryption_policy_name = self._generate_short_name(self.collection_name, "enc")
            encryption_policy = {
                "Rules": [
                    {
                        "ResourceType": "collection",
                        "Resource": [f"collection/{self.collection_name}"]
                    }
                ],
                "AWSOwnedKey": True
            }

            try:
                self.aoss_client.get_security_policy(
                    name=encryption_policy_name,
                    type='encryption'
                )
                logger.info(f"📁 기존 암호화 정책 사용: {encryption_policy_name}")
            except ClientError:
                self.aoss_client.create_security_policy(
                    name=encryption_policy_name,
                    type='encryption',
                    policy=json.dumps(encryption_policy),
                    description=f"Encryption policy for {self.collection_name}"
                )
                logger.info(f"✅ 암호화 정책 생성 완료: {encryption_policy_name}")

            network_policy_name = self._generate_short_name(self.collection_name, "net")
            network_policy = [
                {
                    "Rules": [
                        {
                            "ResourceType": "collection",
                            "Resource": [f"collection/{self.collection_name}"]
                        },
                        {
                            "ResourceType": "dashboard",
                            "Resource": [f"collection/{self.collection_name}"]
                        }
                    ],
                    "AllowFromPublic": True
                }
            ]

            try:
                self.aoss_client.get_security_policy(
                    name=network_policy_name,
                    type='network'
                )
                logger.info(f"📁 기존 네트워크 정책 사용: {network_policy_name}")
            except ClientError:
                self.aoss_client.create_security_policy(
                    name=network_policy_name,
                    type='network',
                    policy=json.dumps(network_policy),
                    description=f"Network policy for {self.collection_name}"
                )
                logger.info(f"✅ 네트워크 정책 생성 완료: {network_policy_name}")

            sts_client = boto3.client('sts', region_name=self.region)
            account_id = sts_client.get_caller_identity()['Account']

            data_policy_name = self._generate_short_name(self.collection_name, "data")
            data_policy = [
                {
                    "Rules": [
                        {
                            "ResourceType": "collection",
                            "Resource": [f"collection/{self.collection_name}"],
                            "Permission": [
                                "aoss:CreateCollectionItems",
                                "aoss:DeleteCollectionItems",
                                "aoss:UpdateCollectionItems",
                                "aoss:DescribeCollectionItems"
                            ]
                        },
                        {
                            "ResourceType": "index",
                            "Resource": [f"index/{self.collection_name}/*"],
                            "Permission": [
                                "aoss:CreateIndex",
                                "aoss:DeleteIndex",
                                "aoss:UpdateIndex",
                                "aoss:DescribeIndex",
                                "aoss:ReadDocument",
                                "aoss:WriteDocument"
                            ]
                        }
                    ],
                    "Principal": [
                        f"arn:aws:iam::{account_id}:root"
                    ]
                }
            ]

            try:
                self.aoss_client.get_access_policy(
                    name=data_policy_name,
                    type='data'
                )
                logger.info(f"📁 기존 데이터 액세스 정책 사용: {data_policy_name}")
            except ClientError:
                self.aoss_client.create_access_policy(
                    name=data_policy_name,
                    type='data',
                    policy=json.dumps(data_policy),
                    description=f"Data access policy for {self.collection_name}"
                )
                logger.info(f"✅ 데이터 액세스 정책 생성 완료: {data_policy_name}")

            time.sleep(5)
            return True

        except ClientError as e:
            logger.error(f"❌ OpenSearch 보안 정책 생성 실패: {e}")
            return False

    def create_opensearch_collection(self) -> Optional[str]:
        try:
            if not self.create_opensearch_security_policies():
                return None

            try:
                response = self.aoss_client.batch_get_collection(names=[self.collection_name])
                if response['collectionDetails']:
                    collection_arn = response['collectionDetails'][0]['arn']
                    status = response['collectionDetails'][0]['status']
                    if status == 'ACTIVE':
                        logger.info(f"📁 기존 OpenSearch 컬렉션 사용: {collection_arn}")
                        return collection_arn
                    else:
                        logger.info(f"⏳ 기존 컬렉션 상태 확인 중: {status}")
                        return self._wait_for_collection_active(collection_arn)
            except:
                pass

            logger.info(f"🚀 새 OpenSearch 컬렉션 생성 중: {self.collection_name}")
            response = self.aoss_client.create_collection(
                name=self.collection_name,
                type='VECTORSEARCH',
                description=f"Vector search collection for {self.kb_name}"
            )

            collection_id = response['createCollectionDetail']['id']
            logger.info(f"📝 OpenSearch 컬렉션 ID: {collection_id}")

            collection_arn = self._wait_for_collection_active(collection_id)
            return collection_arn

        except ClientError as e:
            logger.error(f"❌ OpenSearch 컬렉션 생성 실패: {e}")
            return None

    def _wait_for_collection_active(self, collection_identifier: str) -> Optional[str]:
        """OpenSearch 컬렉션이 활성화될 때까지 대기"""
        max_wait_time = 600  # 10분
        wait_interval = 30  # 30초마다 확인
        elapsed_time = 0

        while elapsed_time < max_wait_time:
            try:
                if collection_identifier.startswith('arn:'):
                    collection_name = collection_identifier.split('/')[-1]
                    status_response = self.aoss_client.batch_get_collection(names=[collection_name])
                else:
                    status_response = self.aoss_client.batch_get_collection(ids=[collection_identifier])

                if status_response['collectionDetails']:
                    status = status_response['collectionDetails'][0]['status']
                    if status == 'ACTIVE':
                        collection_arn = status_response['collectionDetails'][0]['arn']
                        logger.info(f"✅ OpenSearch 컬렉션 활성화 완료: {collection_arn}")
                        return collection_arn
                    elif status == 'FAILED':
                        logger.error("❌ OpenSearch 컬렉션 생성 실패")
                        return None
                    else:
                        logger.info(f"⏳ OpenSearch 컬렉션 상태: {status} (대기 시간: {elapsed_time}초)")

                time.sleep(wait_interval)
                elapsed_time += wait_interval

            except Exception as e:
                logger.error(f"❌ 컬렉션 상태 확인 실패: {e}")
                return None

        logger.error(f"❌ OpenSearch 컬렉션 생성 시간 초과 ({max_wait_time}초)")
        return None

    def create_opensearch_index(self, collection_arn: str) -> bool:
        """OpenSearch 인덱스 생성"""
        try:
            import requests
            from requests_aws4auth import AWS4Auth

            credentials = boto3.Session().get_credentials()
            awsauth = AWS4Auth(
                credentials.access_key,
                credentials.secret_key,
                self.region,
                'aoss',
                session_token=credentials.token
            )

            collection_id = collection_arn.split('/')[-1]
            host = f"https://{collection_id}.{self.region}.aoss.amazonaws.com"

            index_name = self.config['opensearch']['index_name']

            check_url = f"{host}/{index_name}"
            check_response = requests.head(check_url, auth=awsauth)

            if check_response.status_code == 200:
                logger.info(f"📁 기존 OpenSearch 인덱스 사용: {index_name}")
                return True

            logger.info(f"🚀 새 OpenSearch 인덱스 생성 중: {index_name}")

            index_mapping = {
                "settings": {
                    "index": {
                        "knn": True,
                        "knn.algo_param.ef_search": 512
                    }
                },
                "mappings": {
                    "properties": {
                        self.config['opensearch']['vector_field']: {
                            "type": "knn_vector",
                            "dimension": 1536,
                            "method": {
                                "name": "hnsw",
                                "space_type": "l2",
                                "engine": "faiss",
                                "parameters": {
                                    "ef_construction": 512,
                                    "m": 16
                                }
                            }
                        },
                        self.config['opensearch']['text_field']: {
                            "type": "text"
                        },
                        self.config['opensearch']['metadata_field']: {
                            "type": "object"
                        }
                    }
                }
            }

            response = requests.put(
                check_url,
                json=index_mapping,
                auth=awsauth,
                headers={'Content-Type': 'application/json'}
            )

            if response.status_code in [200, 201]:
                logger.info(f"✅ OpenSearch 인덱스 생성 완료: {index_name}")
                return True
            else:
                logger.error(f"❌ 인덱스 생성 실패: {response.status_code} - {response.text}")
                return False

        except Exception as e:
            logger.error(f"❌ OpenSearch 인덱스 생성 실패: {e}")
            logger.info("💡 pip install requests-aws4auth가 필요할 수 있습니다")
            return False

    def create_knowledge_base(self, role_arn: str, collection_arn: str) -> Optional[str]:
        """Knowledge Base 생성"""
        try:
            try:
                response = self.bedrock_agent.list_knowledge_bases()
                for kb in response.get('knowledgeBaseSummaries', []):
                    if kb['name'] == self.kb_name:
                        kb_id = kb['knowledgeBaseId']
                        logger.info(f"📁 기존 Knowledge Base 사용: {kb_id}")
                        self._save_kb_id(kb_id)
                        return kb_id
            except:
                pass

            if not self.create_opensearch_index(collection_arn):
                logger.error("OpenSearch 인덱스 생성에 실패했습니다.")
                return None

            logger.info(f"🚀 새 Knowledge Base 생성 중: {self.kb_name}")
            response = self.bedrock_agent.create_knowledge_base(
                name=self.kb_name,
                description=self.config['knowledge_base']['description'],
                roleArn=role_arn,
                knowledgeBaseConfiguration={
                    'type': 'VECTOR',
                    'vectorKnowledgeBaseConfiguration': {
                        'embeddingModelArn': f"arn:aws:bedrock:{self.region}::foundation-model/{self.config['bedrock']['embedding_model']}"
                    }
                },
                storageConfiguration={
                    'type': 'OPENSEARCH_SERVERLESS',
                    'opensearchServerlessConfiguration': {
                        'collectionArn': collection_arn,
                        'vectorIndexName': self.config['opensearch']['index_name'],
                        'fieldMapping': {
                            'vectorField': self.config['opensearch']['vector_field'],
                            'textField': self.config['opensearch']['text_field'],
                            'metadataField': self.config['opensearch']['metadata_field']
                        }
                    }
                }
            )

            kb_id = response['knowledgeBase']['knowledgeBaseId']
            logger.info(f"✅ Knowledge Base 생성 완료: {kb_id}")

            self._save_kb_id(kb_id)
            return kb_id

        except ClientError as e:
            logger.error(f"❌ Knowledge Base 생성 실패: {e}")
            return None

    def create_data_source(self, kb_id: str) -> Optional[str]:
        try:
            try:
                response = self.bedrock_agent.list_data_sources(knowledgeBaseId=kb_id)
                for ds in response.get('dataSourceSummaries', []):
                    if f"{self.kb_name}-data-source" in ds['name']:
                        ds_id = ds['dataSourceId']
                        logger.info(f"📁 기존 데이터 소스 사용: {ds_id}")
                        return ds_id
            except:
                pass

            logger.info(f"🚀 새 데이터 소스 생성 중...")
            response = self.bedrock_agent.create_data_source(
                knowledgeBaseId=kb_id,
                name=f"{self.kb_name}-data-source",
                description="Educational Q&A data source",
                dataSourceConfiguration={
                    'type': 'S3',
                    's3Configuration': {
                        'bucketArn': f"arn:aws:s3:::{self.bucket_name}",
                        'inclusionPrefixes': [self.config['s3']['prefix']]
                    }
                }
            )

            ds_id = response['dataSource']['dataSourceId']
            logger.info(f"✅ 데이터 소스 생성 완료: {ds_id}")

            return ds_id

        except ClientError as e:
            logger.error(f"❌ 데이터 소스 생성 실패: {e}")
            return None

    def start_ingestion(self, kb_id: str, ds_id: str) -> bool:
        try:
            response = self.bedrock_agent.start_ingestion_job(
                knowledgeBaseId=kb_id,
                dataSourceId=ds_id
            )

            job_id = response['ingestionJob']['ingestionJobId']
            logger.info(f"🚀 데이터 수집 작업 시작: {job_id}")

            max_wait_time = 1800
            wait_interval = 30
            elapsed_time = 0

            while elapsed_time < max_wait_time:
                try:
                    status_response = self.bedrock_agent.get_ingestion_job(
                        knowledgeBaseId=kb_id,
                        dataSourceId=ds_id,
                        ingestionJobId=job_id
                    )

                    status = status_response['ingestionJob']['status']

                    if status == 'COMPLETE':
                        logger.info("✅ 데이터 수집 완료!")
                        return True
                    elif status == 'FAILED':
                        logger.error("❌ 데이터 수집 실패")
                        failure_reasons = status_response['ingestionJob'].get('failureReasons', [])
                        for reason in failure_reasons:
                            logger.error(f"실패 원인: {reason}")
                        return False
                    else:
                        logger.info(f"⏳ 데이터 수집 진행중... 상태: {status} (경과 시간: {elapsed_time}초)")

                    time.sleep(wait_interval)
                    elapsed_time += wait_interval

                except Exception as e:
                    logger.error(f"상태 확인 실패: {e}")
                    time.sleep(wait_interval)
                    elapsed_time += wait_interval

            logger.error(f"❌ 데이터 수집 시간 초과 ({max_wait_time}초)")
            return False

        except ClientError as e:
            logger.error(f"❌ 데이터 수집 실패: {e}")
            return False

    def _save_kb_id(self, kb_id: str):
        """Knowledge Base ID를 설정 파일에 저장"""
        self.config['knowledge_base']['id'] = kb_id

        with open("LLM/config/config.yaml", 'w', encoding='utf-8') as f:
            yaml.dump(self.config, f, default_flow_style=False)

        logger.info(f"📝 Knowledge Base ID 저장: {kb_id}")

    def setup_complete_pipeline(self):
        """전체 파이프라인 실행"""
        logger.info("🚀 기존 S3를 사용한 Bedrock Knowledge Base 설정을 시작합니다...")

        if not self.upload_documents_to_s3():
            logger.error("문서 업로드에 실패했습니다.")
            return False

        role_arn = self.create_iam_role()
        if not role_arn:
            logger.error("IAM 역할 생성에 실패했습니다.")
            return False

        collection_arn = self.create_opensearch_collection()
        if not collection_arn:
            logger.error("OpenSearch 컬렉션 생성에 실패했습니다.")
            return False

        kb_id = self.create_knowledge_base(role_arn, collection_arn)
        if not kb_id:
            logger.error("Knowledge Base 생성에 실패했습니다.")
            return False

        ds_id = self.create_data_source(kb_id)
        if not ds_id:
            logger.error("데이터 소스 생성에 실패했습니다.")
            return False

        if not self.start_ingestion(kb_id, ds_id):
            logger.error("데이터 수집에 실패했습니다.")
            return False

        logger.info("🎉 AWS Bedrock Knowledge Base 설정이 완료되었습니다!")
        logger.info(f"📊 Statistics:")
        logger.info(f"   • Knowledge Base ID: {kb_id}")
        logger.info(f"   • Data Source ID: {ds_id}")
        logger.info(f"   • S3 Bucket: {self.bucket_name}")
        logger.info(f"   • S3 Prefix: {self.config['s3']['prefix']}")
        logger.info(f"   • Collection: {self.collection_name}")

        return True


if __name__ == "__main__":
    setup = BedrockSetupWithExistingS3()
    setup.setup_complete_pipeline()