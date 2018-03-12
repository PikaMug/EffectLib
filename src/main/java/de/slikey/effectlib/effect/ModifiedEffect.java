package de.slikey.effectlib.effect;

import de.slikey.effectlib.Effect;
import de.slikey.effectlib.EffectManager;
import de.slikey.effectlib.EffectType;
import de.slikey.effectlib.math.EquationStore;
import de.slikey.effectlib.math.EquationTransform;
import org.bukkit.configuration.ConfigurationSection;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

public class ModifiedEffect extends Effect {

    /**
     * The base configuration of the inner effect.
     */
    public ConfigurationSection effect;

    /**
     * The class name of the effect to modify.
     *
     * Can be left blank if class is set in effect configuration.
     */
    public String effectClass;

    /**
     * Effect parameters to modify each tick, paired with an equation used to modify them.
     */
    public Map<String, String> parameters = new HashMap<String, String>();

    /**
     * The variable name used in equations to represent major ticks
     */
    public String variable = "t";

    public ModifiedEffect(EffectManager effectManager) {
        super(effectManager);
        type = EffectType.REPEATING;
        period = 1;
        iterations = 100;
    }

    private boolean initialized = false;
    private Effect innerEffect;
    private Map<Field, EquationTransform> parameterTransforms = new HashMap<Field, EquationTransform>();
    private int step = 0;

    @Override
    public void onRun() {
        if (!initialized) {
            initialized = true;
            if (effect == null) {
                effectManager.onError("ModifiedEffect missing inner effect configuration");
                cancel();
                return;
            }

            if (effectClass == null) {
                effectClass = effect.getString("class");
            }
            if (effectClass == null) {
                effectManager.onError("ModifiedEffect missing inner effect class property");
                cancel();
                return;
            }

            innerEffect = effectManager.getEffect(effectClass, effect, origin, target, null, targetPlayer);
            for (Map.Entry<String, String> entry : parameters.entrySet()) {
                String equation = entry.getValue();
                String fieldName = entry.getKey();
                EquationTransform transform = EquationStore.getInstance().getTransform(equation, variable);
                Exception ex = transform.getException();
                if (ex != null) {
                    effectManager.onError("Error parsing equation: " + equation, ex);
                    continue;
                }
                try {
                    Field field = innerEffect.getClass().getField(fieldName);
                    parameterTransforms.put(field, transform);
                } catch (Exception ex2) {
                    effectManager.onError("Error binding to field: " + fieldName + " of effect class " + effectClass, ex2);
                    continue;
                }
            }
        }
        if (innerEffect == null) {
            return;
        }
        innerEffect.prepare();

        for (Map.Entry<Field, EquationTransform> entry : parameterTransforms.entrySet()) {
            double value = entry.getValue().get(step);
            try {
                entry.getKey().set(innerEffect, value);
            } catch (Exception ex) {
                effectManager.onError("Error assigning to : " + entry.getKey().getName() + " of effect class " + effectClass, ex);
                cancel();
            }
        }

        try {
            innerEffect.onRun();
        } catch (Exception ex) {
            innerEffect.onDone();
            effectManager.onError(ex);
        }
        step++;
    }
}