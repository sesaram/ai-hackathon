import cv2
import torch
from yolov5.preproc import makePersonBox
from math import sqrt

model = torch.hub.load('yolov5', 'custom', path='yolov5s.pt', source='local')

cap = cv2.VideoCapture(0)
w = round(cap.get(cv2.CAP_PROP_FRAME_WIDTH))
h = round(cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
CONST_CENTER = 300 * 345
CONST_1PX = (180 / 345) / 300

while cap.isOpened():
    success, image = cap.read()

    if not success:
        continue

    image = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
    results = model(image)
    result = makePersonBox(results)

    for key in result.keys():
        lb = result[key]['left_bottom']
        rt = result[key]['right_top']
        mid = ((rt[0]+lb[0])/2, (rt[1]+lb[1])/2)
        hei = rt[1]-lb[1]
        image = cv2.rectangle(image, (round(lb[0]), round(lb[1])), (round(rt[0]), round(rt[1])), (0, 0, 255), 4)
        raw_distance = (CONST_CENTER/hei).item()
        diff_center = abs(320 - mid[0]).item() * raw_distance * CONST_1PX
        distance = sqrt(raw_distance ** 2 + diff_center ** 2)
        
        print(key, distance)

    image = cv2.cvtColor(image, cv2.COLOR_RGB2BGR)
    cv2.imshow('mouse and keyboard', image)

    key_in = cv2.waitKey(5)
    if key_in == ord('q'):
        break