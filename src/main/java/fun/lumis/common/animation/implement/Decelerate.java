package fun.lumis.common.animation.implement;

import fun.lumis.common.animation.Animation;

public class Decelerate extends Animation {

    @Override
    public double calculation(double value) {
        double x = value / ms;
        return 1 - (x - 1) * (x - 1);
    }
}
