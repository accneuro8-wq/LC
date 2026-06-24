package fun.lumis.common.animation.inovated;

import lombok.Getter;
import lombok.Setter;
import fun.lumis.utils.math.calc.Calculate;

public class InovatedAnimService {

    @Setter @Getter
    private float value, prevValue;
    private float animationSpeed;
    private float fromValue, toValue;
    @Getter @Setter
    private float animationValue;

    public void update(boolean update) {
        prevValue = value;
        value = Calculate.clamp(value + (update ? animationSpeed : -animationSpeed), fromValue, toValue);
    }

    public void animate(float fromValue, float toValue, float animationSpeed, EasingList.Easing easing, float partialTicks) {
        this.animationSpeed = animationSpeed;
        this.fromValue = fromValue;
        this.toValue = toValue;
        animationValue = easing.ease(Calculate.interpolate(prevValue, value, partialTicks));
    }

}
