package streams

import streams.config.StreamsConfig
import streams.extensions.toPointCase
import streams.utils.JSONUtils
import streams.service.TopicUtils
import streams.service.TopicValidationException
import streams.service.Topics
import streams.service.sink.strategy.SourceIdIngestionStrategyConfig
import java.util.concurrent.TimeUnit

data class StreamsSinkConfiguration(val enabled: Boolean = StreamsConfig.SINK_ENABLED_VALUE,
                                    val proceduresEnabled: Boolean = StreamsConfig.PROCEDURES_ENABLED_VALUE,
                                    val topics: Topics = Topics(),
                                    val errorConfig: Map<String,Any?> = emptyMap(),
                                    val checkApocTimeout: Long = -1,
                                    val checkApocInterval: Long = 1000,
                                    val clusterOnly: Boolean = false,
                                    val checkWriteableInstanceInterval: Long = TimeUnit.MINUTES.toMillis(3),
                                    val pollInterval: Long = TimeUnit.SECONDS.toMillis(0),
                                    val sourceIdStrategyConfig: SourceIdIngestionStrategyConfig = SourceIdIngestionStrategyConfig()) {

    fun asMap(): Map<String, Any?> {
        val configMap = JSONUtils.asMap(this)
                .filterKeys { it != "topics" && it != "enabled" && it != "proceduresEnabled" && !it.startsWith("check") }
                .mapKeys { it.key.toPointCase() }
                .mapKeys {
                    when (it.key) {
                        "error.config" -> "streams.sink.errors"
                        "procedures.enabled" -> "streams.${it.key}"
                        "cluster.only" -> "streams.${it.key}"
                        else -> if (it.key.startsWith("streams.sink")) it.key else "streams.sink.${it.key}"
                    }
                }
        val topicMap = this.topics.asMap()
                .mapKeys { it.key.key }
        val invalidTopics = mapOf("invalid_topics" to this.topics.invalid)
        return (configMap + topicMap + invalidTopics)
    }

    companion object {
        fun from(configMap: Map<String, String>, dbName: String, invalidTopics: List<String> = emptyList(), isDefaultDb: Boolean): StreamsSinkConfiguration {
            val default = StreamsSinkConfiguration()

            var topics = Topics.from(map = configMap, dbName = dbName, invalidTopics = invalidTopics)
            if (isDefaultDb) {
                topics += Topics.from(map = configMap, invalidTopics = invalidTopics)
            }

            TopicUtils.validate<TopicValidationException>(topics)

            val sourceIdStrategyConfig = createSourceIdIngestionStrategyConfig(configMap, dbName, isDefaultDb)

            val errorHandler = configMap
                    .filterKeys { it.startsWith("streams.sink.error") }
                    .mapKeys { it.key.substring("streams.sink.".length) }


            return default.copy(enabled = StreamsConfig.isSinkEnabled(configMap, dbName),
                    proceduresEnabled = StreamsConfig.hasProceduresEnabled(configMap, dbName),
                    topics = topics,
                    errorConfig = errorHandler,
                    checkApocTimeout = configMap.getOrDefault(StreamsConfig.CHECK_APOC_TIMEOUT,
                            default.checkApocTimeout)
                            .toString()
                            .toLong(),
                    checkApocInterval = configMap.getOrDefault(StreamsConfig.CHECK_APOC_INTERVAL,
                            default.checkApocInterval)
                            .toString()
                            .toLong(),
                    checkWriteableInstanceInterval = configMap.getOrDefault(StreamsConfig.CHECK_WRITEABLE_INSTANCE_INTERVAL,
                            default.checkWriteableInstanceInterval)
                            .toString().toLong(),
                    pollInterval = configMap.getOrDefault(StreamsConfig.POLL_INTERVAL, default.pollInterval)
                            .toString().toLong(),
                    clusterOnly = configMap.getOrDefault(StreamsConfig.CLUSTER_ONLY,
                            default.clusterOnly)
                            .toString().toBoolean(),
                    sourceIdStrategyConfig = sourceIdStrategyConfig)
        }

        fun createSourceIdIngestionStrategyConfig(configMap: Map<String, String>, dbName: String, isDefaultDb: Boolean): SourceIdIngestionStrategyConfig {
            val sourceIdStrategyConfigPrefix = "streams.sink.topic.cdc.sourceId"
            val (sourceIdStrategyLabelNameKey, sourceIdStrategyIdNameKey) = if (isDefaultDb) {
                "labelName" to "idName"
            } else {
                "labelName.to.$dbName" to "idName.to.$dbName"
            }
            val defaultSourceIdStrategyConfig = SourceIdIngestionStrategyConfig()
            return SourceIdIngestionStrategyConfig(
                    configMap.getOrDefault("$sourceIdStrategyConfigPrefix.$sourceIdStrategyLabelNameKey", defaultSourceIdStrategyConfig.labelName),
                    configMap.getOrDefault("$sourceIdStrategyConfigPrefix.$sourceIdStrategyIdNameKey", defaultSourceIdStrategyConfig.idName))
        }

    }

}