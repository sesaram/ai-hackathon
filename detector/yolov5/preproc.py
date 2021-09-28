def makePersonBox(input):
    results = input.pandas().xyxy[0]
    results = results[results["class"] == 0]
    result = {}
    for i in range(len(results)):
        a = i + 1
        l = results.loc[i]
        
        result[f"person{a}"] = {"left_bottom" : (l["xmin"], l["ymin"]), "right_bottom" : (l["xmax"], l["ymin"]), "left_top" : (l["xmin"], l["ymax"]), "right_top" : (l["xmax"],l["ymax"])}
    return result
