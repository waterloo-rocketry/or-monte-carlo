package com.waterloorocketry.openrocket_monte_carlo.aspect;


import com.waterloorocketry.openrocket_monte_carlo.gui.MultiLevelWindTable;
import info.openrocket.core.models.wind.MultiLevelPinkNoiseWindModel;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Aspect
public class ReplaceMultiLevelTableAspect {
    final static Logger logger = LoggerFactory.getLogger(ReplaceMultiLevelTableAspect.class);

    @Around("call(info.openrocket.swing.gui.simulation.MultiLevelWindTable.new(..))")
    public Object replaceMultiLevelTable(ProceedingJoinPoint pjp) throws Throwable {

        logger.debug("Replacing MultiLevelWindTable");

        return new MultiLevelWindTable((MultiLevelPinkNoiseWindModel) pjp.getArgs()[0]);
    }
}
