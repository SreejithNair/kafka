/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.kafka.streams.processor.api;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.streams.StreamsMetrics;
import org.apache.kafka.streams.processor.Cancellable;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.Punctuator;
import org.apache.kafka.streams.processor.StateStore;
import org.apache.kafka.streams.processor.TaskId;
import org.apache.kafka.streams.processor.TimestampExtractor;

import java.io.File;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Processor context interface.
 *
 * @param <KForward> a bound on the types of keys that may be forwarded
 * @param <VForward> a bound on the types of values that may be forwarded
 */
public interface ProcessorContext<KForward, VForward> {

    /**
     * Returns the application id.
     *
     * @return the application id
     */
    String applicationId();

    /**
     * Returns the task id.
     *
     * @return the task id
     */
    TaskId taskId();

    /**
     * The metadata of the record, if it is defined. Note that as long as the processor is
     * receiving a record downstream of a Source (i.e., the current record is coming from an
     * input topic), the metadata is defined. On the other hand, if a parent processor has
     * registered a punctuator and called {@link ProcessorContext#forward(Record)} from that
     * punctuator, then there is no record from an input topic, and therefore the metadata
     * would be undefined.
     */
    Optional<RecordMetadata> recordMetadata();

    /**
     * Returns the default key serde.
     *
     * @return the key serializer
     */
    Serde<?> keySerde();

    /**
     * Returns the default value serde.
     *
     * @return the value serializer
     */
    Serde<?> valueSerde();

    /**
     * Returns the state directory for the partition.
     *
     * @return the state directory
     */
    File stateDir();

    /**
     * Returns Metrics instance.
     *
     * @return StreamsMetrics
     */
    StreamsMetrics metrics();

    /**
     * Get the state store given the store name.
     *
     * @param name The store name
     * @param <S> The type or interface of the store to return
     * @return The state store instance
     *
     * @throws ClassCastException if the return type isn't a type or interface of the actual returned store.
     */
    <S extends StateStore> S getStateStore(final String name);

    /**
     * Schedules a periodic operation for processors. A processor may call this method during
     * {@link Processor#init(ProcessorContext) initialization} or
     * {@link Processor#process(Record)}  processing} to
     * schedule a periodic callback &mdash; called a punctuation &mdash; to {@link Punctuator#punctuate(long)}.
     * The type parameter controls what notion of time is used for punctuation:
     * <ul>
     *   <li>{@link PunctuationType#STREAM_TIME} &mdash; uses "stream time", which is advanced by the processing of messages
     *   in accordance with the timestamp as extracted by the {@link TimestampExtractor} in use.
     *   The first punctuation will be triggered by the first record that is processed.
     *   <b>NOTE:</b> Only advanced if messages arrive</li>
     *   <li>{@link PunctuationType#WALL_CLOCK_TIME} &mdash; uses system time (the wall-clock time),
     *   which is advanced independent of whether new messages arrive.
     *   The first punctuation will be triggered after interval has elapsed.
     *   <b>NOTE:</b> This is best effort only as its granularity is limited by how long an iteration of the
     *   processing loop takes to complete</li>
     * </ul>
     *
     * <b>Skipping punctuations:</b> Punctuations will not be triggered more than once at any given timestamp.
     * This means that "missed" punctuation will be skipped.
     * It's possible to "miss" a punctuation if:
     * <ul>
     *   <li>with {@link PunctuationType#STREAM_TIME}, when stream time advances more than interval</li>
     *   <li>with {@link PunctuationType#WALL_CLOCK_TIME}, on GC pause, too short interval, ...</li>
     * </ul>
     *
     * @param interval the time interval between punctuations (supported minimum is 1 millisecond)
     * @param type one of: {@link PunctuationType#STREAM_TIME}, {@link PunctuationType#WALL_CLOCK_TIME}
     * @param callback a function consuming timestamps representing the current stream or system time
     * @return a handle allowing cancellation of the punctuation schedule established by this method
     * @throws IllegalArgumentException if the interval is not representable in milliseconds
     */
    Cancellable schedule(final Duration interval,
                         final PunctuationType type,
                         final Punctuator callback);

    /**
     * Forwards a record to all child processors.
     *
     * @param record The record to forward to all children
     */
    <K extends KForward, V extends VForward> void forward(Record<K, V> record);

    /**
     * Forwards a record to the specified child processor.
     *
     * @param record The record to forward
     * @param childName The name of the child processor to receive the record
     */
    <K extends KForward, V extends VForward> void forward(Record<K, V> record, final String childName);

    /**
     * Requests a commit.
     */
    void commit();

    /**
     * Returns all the application config properties as key/value pairs.
     *
     * <p> The config properties are defined in the {@link org.apache.kafka.streams.StreamsConfig}
     * object and associated to the ProcessorContext.
     *
     * <p> The type of the values is dependent on the {@link org.apache.kafka.common.config.ConfigDef.Type type} of the property
     * (e.g. the value of {@link org.apache.kafka.streams.StreamsConfig#DEFAULT_KEY_SERDE_CLASS_CONFIG DEFAULT_KEY_SERDE_CLASS_CONFIG}
     * will be of type {@link Class}, even if it was specified as a String to
     * {@link org.apache.kafka.streams.StreamsConfig#StreamsConfig(Map) StreamsConfig(Map)}).
     *
     * @return all the key/values from the StreamsConfig properties
     */
    Map<String, Object> appConfigs();

    /**
     * Returns all the application config properties with the given key prefix, as key/value pairs
     * stripping the prefix.
     *
     * <p> The config properties are defined in the {@link org.apache.kafka.streams.StreamsConfig}
     * object and associated to the ProcessorContext.
     *
     * @param prefix the properties prefix
     * @return the key/values matching the given prefix from the StreamsConfig properties.
     */
    Map<String, Object> appConfigsWithPrefix(final String prefix);
}
