package com.happysg.radar.compat.cbc;

import com.happysg.radar.compat.sable.SableRadarCompat;
import com.happysg.radar.math3.analysis.UnivariateFunction;
import com.happysg.radar.math3.analysis.solvers.BrentSolver;
import com.happysg.radar.math3.analysis.solvers.UnivariateSolver;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity;
import rbasamoyai.createbigcannons.cannon_control.contraption.AbstractMountedCannonContraption;
import rbasamoyai.createbigcannons.cannon_control.contraption.PitchOrientedContraptionEntity;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Double.NaN;
import static java.lang.Math.log;
import static java.lang.Math.toRadians;

public class CannonTargeting {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static List<Double> directPitchToTarget(Vec3 originPos, Vec3 targetPos) {
        double dX = Math.hypot(targetPos.x - originPos.x, targetPos.z - originPos.z);
        double dY = targetPos.y - originPos.y;
        return List.of(Math.toDegrees(Math.atan2(dY, dX)));
    }

    public static double calculateProjectileYatX(double speed, double dX, double thetaRad, double drag, double g) {
        double cos = Math.cos(thetaRad);
        if (Math.abs(cos) < 1.0e-6) {
            return NaN;
        }

        if (Math.abs(drag) < 1.0e-6) {
            double cos2 = cos * cos;
            return dX * Math.tan(thetaRad)
                    - (g * dX * dX) / (2.0 * speed * speed * cos2);
        }

        double logArg = 1 - (drag * dX) / (speed * cos);
        if (logArg <= 0.0) {
            return NaN;
        }

        double l = log(logArg);
        if (Double.isInfinite(l)) l = NaN;
        return dX * Math.tan(thetaRad)
                + (dX * g) / (drag * speed * cos)
                + g * l / (drag * drag);
    }

    public static List<Double> calculatePitch(
            CannonMountBlockEntity mount,
            Vec3 originPos,
            Vec3 targetPos,
            ServerLevel level
    ) {
        if (mount == null || targetPos == null || originPos == null) return null;

        PitchOrientedContraptionEntity contraption = mount.getContraption();
        if (contraption == null || !(contraption.getContraption() instanceof AbstractMountedCannonContraption cannon)) return null;

        if (CannonUtil.isLaserCannon(cannon)) {
            return directPitchToTarget(originPos, targetPos);
        }

        float speed = CannonUtil.getInitialVelocity(cannon, level);
        double drag = Math.max(0.0, CannonUtil.getProjectileDrag(cannon, level));
        double gravity = CannonUtil.getProjectileGravity(cannon, level);
        if (!Double.isFinite(speed) || speed <= 0) return directPitchToTarget(originPos, targetPos);

        double dX = Math.hypot(targetPos.x - originPos.x, targetPos.z - originPos.z);
        double dY = targetPos.y - originPos.y;
        double g = Math.abs(gravity);

        UnivariateFunction diffFunction = theta -> {
            double thetaRad = toRadians(theta);
            double y = calculateProjectileYatX(speed, dX, thetaRad, drag, g);
            return y - dY;
        };

        UnivariateSolver solver = new BrentSolver(1e-32);

        double start = -90, end = 90, step = 1.0;
        List<Double> roots = new ArrayList<>();

        double prevValue = NaN;
        double prevTheta = start;

        for (double theta = start + step; theta <= end; theta += step) {
            double currValue = diffFunction.value(theta);
            if (!Double.isFinite(currValue)) {
                continue;
            }

            if (Double.isFinite(prevValue) && prevValue * currValue < 0) {
                try {
                    double root = solver.solve(1000, diffFunction, prevTheta, theta);
                    roots.add(root);
                } catch (Exception e) {
                    return null;
                }
            }

            prevTheta = theta;
            prevValue = currValue;
        }

        return roots.isEmpty() ? null : roots;
    }

    // OLD: legacy origin
    public static List<Double> calculatePitch(CannonMountBlockEntity mount, Vec3 targetPos, ServerLevel level) {
        if (mount == null || targetPos == null) return null;
        Vec3 originPos = SableRadarCompat.projectToWorld(level, mount.getBlockPos().above(2).getCenter());
        return calculatePitch(mount, originPos, targetPos, level);
    }
}
