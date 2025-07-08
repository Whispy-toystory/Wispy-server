import json
import os
from pathlib import Path
from datetime import datetime
import pandas as pd


class DataPreprocessor:
    """Educational Q&A 데이터를 AWS Bedrock Knowledge Base 형식으로 변환"""

    def __init__(self, base_dir="LLM"):
        self.base_dir = Path(base_dir)
        self.raw_data_dir = self.base_dir / "data" / "raw"
        self.processed_data_dir = self.base_dir / "data" / "processed" / "knowledge_base"

        self.processed_data_dir.mkdir(parents=True, exist_ok=True)

    def classify_question_type(self, question: str) -> str:
        question_lower = question.lower()

        if any(word in question_lower for word in ['what', 'who', 'where', 'when']):
            return 'factual'
        elif any(word in question_lower for word in ['how', 'why']):
            return 'explanatory'
        elif any(word in question_lower for word in ['should', 'can', 'do']):
            return 'instructional'
        elif any(word in question_lower for word in ['name', 'think', 'remember']):
            return 'recall'
        else:
            return 'general'

    def classify_topic(self, question: str, answer: str) -> str:
        text = (question + " " + answer).lower()

        if any(word in text for word in ['food', 'eat', 'cook', 'meal', 'healthy', 'nutrition']):
            return 'nutrition_cooking'
        elif any(word in text for word in ['body', 'health', 'doctor', 'medicine', 'exercise']):
            return 'health_body'
        elif any(word in text for word in ['animal', 'plant', 'nature', 'environment']):
            return 'nature_science'
        elif any(word in text for word in ['friend', 'family', 'feel', 'emotion', 'kind']):
            return 'social_emotional'
        elif any(word in text for word in ['number', 'count', 'shape', 'measure']):
            return 'math_numbers'
        elif any(word in text for word in ['word', 'letter', 'read', 'write', 'language']):
            return 'language_literacy'
        elif any(word in text for word in ['sport', 'play', 'game', 'run', 'jump']):
            return 'physical_activity'
        elif any(word in text for word in ['art', 'color', 'draw', 'paint', 'music']):
            return 'arts_creativity'
        elif any(word in text for word in ['safe', 'rule', 'help', 'community']):
            return 'safety_community'
        else:
            return 'general_knowledge'

    def estimate_age_group(self, question: str, answer: str) -> str:
        text = question + " " + answer

        if len(answer.split()) < 15 and any(word in text.lower() for word in ['yes', 'no', 'simple']):
            return '3-4'
        elif len(answer.split()) < 25:
            return '4-6'
        elif 'because' in answer.lower() or 'why' in question.lower():
            return '6-8'
        else:
            return '5-7'

    def preprocess_data(self, input_file: str = "train.json"):
        input_path = self.raw_data_dir / input_file

        print(f"Reading data from: {input_path}")

        try:
            with open(input_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
        except FileNotFoundError:
            print(f"Error: {input_path} 파일을 찾을 수 없습니다.")
            return

        processed_docs = []

        for idx, item in enumerate(data):
            question = item.get('Question', '')
            answer = item.get('Answer', '')

            question_type = self.classify_question_type(question)
            topic = self.classify_topic(question, answer)
            age_group = self.estimate_age_group(question, answer)

            doc = {
                "id": f"edu_qa_{idx:04d}",
                "title": f"Educational Q&A: {question[:50]}...",
                "content": f"Question: {question}\n\nAnswer: {answer}",
                "metadata": {
                    "type": "educational_qa",
                    "question_type": question_type,
                    "topic": topic,
                    "age_group": age_group,
                    "source": "educational_dataset",
                    "language": "english",
                    "created_at": datetime.now().isoformat(),
                    "content_length": len(answer.split()),
                    "difficulty": "beginner" if len(answer.split()) < 20 else "intermediate"
                }
            }

            processed_docs.append(doc)

            output_file = self.processed_data_dir / f"{doc['id']}.json"

            with open(output_file, 'w', encoding='utf-8') as f:
                json.dump({
                    "title": doc['title'],
                    "content": doc['content'],
                    "metadata": doc['metadata']
                }, f, indent=2, ensure_ascii=False)

        print(f"✅ 처리 완료: {len(processed_docs)}개의 문서가 생성되었습니다.")

        self.print_statistics(processed_docs)

        self.save_statistics(processed_docs)

        return processed_docs

    def print_statistics(self, docs):
        print("\n📊 데이터 통계:")
        print(f"총 문서 수: {len(docs)}")

        topics = [doc['metadata']['topic'] for doc in docs]
        topic_counts = pd.Series(topics).value_counts()
        print(f"\n주제별 분포:")
        for topic, count in topic_counts.items():
            print(f"  {topic}: {count}개")

        question_types = [doc['metadata']['question_type'] for doc in docs]
        type_counts = pd.Series(question_types).value_counts()
        print(f"\n질문 유형별 분포:")
        for qtype, count in type_counts.items():
            print(f"  {qtype}: {count}개")

        age_groups = [doc['metadata']['age_group'] for doc in docs]
        age_counts = pd.Series(age_groups).value_counts()
        print(f"\n연령대별 분포:")
        for age, count in age_counts.items():
            print(f"  {age}세: {count}개")

    def save_statistics(self, docs):
        stats_data = []

        for doc in docs:
            stats_data.append({
                'id': doc['id'],
                'topic': doc['metadata']['topic'],
                'question_type': doc['metadata']['question_type'],
                'age_group': doc['metadata']['age_group'],
                'content_length': doc['metadata']['content_length'],
                'difficulty': doc['metadata']['difficulty']
            })

        stats_df = pd.DataFrame(stats_data)
        stats_file = self.base_dir / "data" / "processed" / "dataset_statistics.csv"
        stats_df.to_csv(stats_file, index=False)

        print(f"📈 통계 정보가 저장되었습니다: {stats_file}")


if __name__ == "__main__":
    preprocessor = DataPreprocessor()

    print("🚀 데이터 전처리를 시작합니다...")
    processed_docs = preprocessor.preprocess_data()

    if processed_docs:
        print(f"\n✅ 전처리 완료! {len(processed_docs)}개의 문서가 준비되었습니다.")
        print(f"📁 처리된 파일들은 다음 위치에 저장되었습니다:")
        print(f"   {preprocessor.processed_data_dir}")
        print(f"\n다음 단계: AWS Bedrock Knowledge Base 설정")