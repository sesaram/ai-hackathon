package ai.hackathon.camera2;

public class Distance {
    float getDistance(float xl, float yb, float xr, float yt){
        float h = yt -yb;
        int center = 450 * 250;
        int px = 180/center;
        float raw_distance = center/h;
        float diff_center = Math.abs(320 - (xr/2 - xl/2))*raw_distance*px;
        float distance = (float)Math.sqrt(Math.pow(raw_distance,2)+Math.pow(diff_center,2));
        return distance/300;
    }

    int getDirection(float xl,float xr){
        int time = (int)320/5;
        int direction = (int)(((xr + xl)/2) / time + 10.5);
        return direction;
    }
};