import cv2
import torch
from PIL import Image
from yolov5.preproc import makePersonBox

model = torch.hub.load('yolov5', 'custom', path='yolov5s.pt', source='local')

#이미지 로드
img = Image.open('example.jpg')
results = model(img)

#결과
result = makePersonBox(results)
print(result)