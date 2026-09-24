package com.examprep.common.entity;

import com.examprep.common.util.Uuids;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.BeforeExecutionGenerator;
import org.hibernate.generator.EventType;
import org.hibernate.generator.EventTypeSets;

import java.util.EnumSet;

/** Hibernate generator backing {@link UuidV7}. The id is assigned in memory before the INSERT. */
public class UuidV7Generator implements BeforeExecutionGenerator {

    @Override
    public Object generate(SharedSessionContractImplementor session, Object owner, Object currentValue,
                           EventType eventType) {
        return Uuids.v7();
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
