from datasets import load_dataset

simple_wiki = load_dataset("rahular/simple-wikipedia", split="train")
simple_wiki.save_to_disk("data/simple_wiki")

simple_qa = load_dataset("basicv8vc/SimpleQA", split="test")
simple_qa.save_to_disk("data/simple_qa")

print("💾  download 완료")
