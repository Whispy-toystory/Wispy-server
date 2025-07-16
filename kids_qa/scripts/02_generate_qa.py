import uuid, textstat, tqdm, json, multiprocessing as mp
from datasets import load_from_disk, Dataset
from transformers import pipeline

wiki_ds = load_from_disk("data/simple_wiki")

qg = pipeline("question-generation", model="iarfmoose/t5-base-question-generator")

def para_filter(record):
    for para in record["text"].split("\n\n"):
        p = para.strip()
        if 30 < len(p) < 400 and textstat.text_standard(p, float_output=True) <= 3:
            yield p

def make_qa(para):
    qa = qg(para)[0]   # {'question', 'answer'}
    return {
        "id": str(uuid.uuid4()),
        "question": qa["question"],
        "answer": qa["answer"],
        "source": "simplewiki",
        "reading_level": "grade2"
    }

paras = (p for rec in wiki_ds for p in para_filter(rec))

with mp.Pool() as pool:
    qa_records = list(tqdm.tqdm(pool.imap(make_qa, paras), total=5000))

Dataset.from_list(qa_records).save_to_disk("data/simple_wiki_qa")
print("✨ simple_wiki → Q&A 변환 완료")
