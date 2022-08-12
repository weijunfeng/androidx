/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.health.connect.client.records

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Velocity
import java.time.Instant
import java.time.ZoneOffset

/**
 * Captures the user's speed, e.g. during running or cycling. Each record represents a series of
 * measurements.
 */
public class SpeedRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    override val samples: List<Sample>,
    override val metadata: Metadata = Metadata.EMPTY,
) : SeriesRecord<SpeedRecord.Sample> {

    /*
     * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SpeedRecord) return false

        if (startTime != other.startTime) return false
        if (startZoneOffset != other.startZoneOffset) return false
        if (endTime != other.endTime) return false
        if (endZoneOffset != other.endZoneOffset) return false
        if (samples != other.samples) return false
        if (metadata != other.metadata) return false

        return true
    }

    /*
     * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
     */
    override fun hashCode(): Int {
        var result = startTime.hashCode()
        result = 31 * result + (startZoneOffset?.hashCode() ?: 0)
        result = 31 * result + endTime.hashCode()
        result = 31 * result + (endZoneOffset?.hashCode() ?: 0)
        result = 31 * result + samples.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    companion object {
        private const val SPEED_TYPE_NAME = "SpeedSeries"
        private const val SPEED_FIELD_NAME = "speed"

        /**
         * Metric identifier to retrieve average speed from
         * [androidx.health.connect.client.aggregate.AggregationResult].
         */
        @JvmField
        val SPEED_AVG: AggregateMetric<Velocity> =
            AggregateMetric.doubleMetric(
                dataTypeName = SPEED_TYPE_NAME,
                aggregationType = AggregateMetric.AggregationType.AVERAGE,
                fieldName = SPEED_FIELD_NAME,
                mapper = Velocity::metersPerSecond,
            )

        /**
         * Metric identifier to retrieve minimum speed from
         * [androidx.health.connect.client.aggregate.AggregationResult].
         */
        @JvmField
        val SPEED_MIN: AggregateMetric<Velocity> =
            AggregateMetric.doubleMetric(
                dataTypeName = SPEED_TYPE_NAME,
                aggregationType = AggregateMetric.AggregationType.MINIMUM,
                fieldName = SPEED_FIELD_NAME,
                mapper = Velocity::metersPerSecond,
            )

        /**
         * Metric identifier to retrieve maximum speed from
         * [androidx.health.connect.client.aggregate.AggregationResult].
         */
        @JvmField
        val SPEED_MAX: AggregateMetric<Velocity> =
            AggregateMetric.doubleMetric(
                dataTypeName = SPEED_TYPE_NAME,
                aggregationType = AggregateMetric.AggregationType.MAXIMUM,
                fieldName = SPEED_FIELD_NAME,
                mapper = Velocity::metersPerSecond,
            )
    }

    /**
     * Represents a single measurement of the speed, a scalar magnitude.
     *
     * @param time The point in time when the measurement was taken.
     * @param speed Speed in [Velocity] unit. Valid range: 0-1000000 meters/sec.
     *
     * @see SpeedRecord
     */
    public class Sample(
        val time: Instant,
        val speed: Velocity,
    ) {

        init {
            speed.requireNotLess(other = speed.zero(), name = "speed")
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Sample) return false

            if (time != other.time) return false
            if (speed != other.speed) return false

            return true
        }

        override fun hashCode(): Int {
            var result = time.hashCode()
            result = 31 * result + speed.hashCode()
            return result
        }
    }
}