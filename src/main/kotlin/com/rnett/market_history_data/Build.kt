package com.rnett.market_history_data

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.rnett.launchpad.Launchpad
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.BadResponseStatusException
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.*
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import java.util.zip.ZipException

object BuildMarketHistoryData {
    @JvmStatic
    fun main(args: Array<String>) {
        build("-localhost" in args)
    }


    data class EsiMarketData(
        val average: Double, val date: String,
        val highest: Double, val lowest: Double,
        @SerializedName("order_count") val orderCount: Long, val volume: Long
    )


    fun decompressGzip(compressed: ByteArray): String {
        try {
            val bis = ByteArrayInputStream(compressed)

            if (String(compressed).let { it.isBlank() || it == "[]" })
                return "[]"

            val gis = GZIPInputStream(bis)
            val br = BufferedReader(InputStreamReader(gis, "UTF-8"))
            val sb = StringBuilder()
            var line: String?
            while (true) {
                line = br.readLine()

                if (line == null)
                    break

                sb.append(line)
            }
            br.close()
            gis.close()
            bis.close()
            return sb.toString()
        } catch (e: ZipException) {
            return String(compressed)
        }
    }

    val client
        get() = HttpClient(Apache) {
            engine {
                connectTimeout *= 100
                connectionRequestTimeout *= 100
                socketTimeout *= 100
            }
        }

    suspend fun getEsiDataFor(typeId: Int, regionId: Int): List<EsiMarketData> {
        var tries = 0
        var exception: Exception? = null

        while (tries < 5) {
            try {
                val json =
                    client.use { client ->
                        client.get<ByteArray>("https://esi.evetech.net/latest/markets/$regionId/history/?datasource=tranquility&type_id=$typeId") {
                            header("Accept-Encoding", "gzip")
                            header("User-Agent", "Ligraph v2 - jnett96@gmail.com")
                        }
                    }


                return Gson().fromJson(decompressGzip(json))


            } catch (e: Exception) {

                if (e is BadResponseStatusException) {
                    if (e.statusCode == HttpStatusCode.NotFound)
                        return listOf()
                }

                exception = e
                delay(200 * tries.toLong())
                println("Retries: ${tries + 1}")
            }
            tries++
        }
        println("Failed on \"https://esi.evetech.net/latest/markets/$regionId/history/?datasource=tranquility&type_id=$typeId\"")
        exception?.printStackTrace()
        if (exception != null)
            throw exception

        throw IllegalStateException("Errored")
    }


    fun build(useLocalhost: Boolean, regionId: Int = 10000002) {
        connectToDB(useLocalhost)

        val alreadyDone = transaction {
            historydatas.run {
                slice(typeid).selectAll().distinct().map { it[typeid] }
            }
        }.toSet()

        val original = usefulTypes.size

        val types = usefulTypes.keys - alreadyDone

        val done = AtomicInteger(alreadyDone.size)

        runBlocking {
            withContext(Dispatchers.Default) {
                val launchpad = Launchpad<Unit>(200, 200)

                types.map { type ->
                    launchpad {
                        val raw = getEsiDataFor(type, regionId)

                        if (raw.size > 7) {

                            val data = raw.sortedByDescending { it.date }.take(60)
                            transaction {
                                historydatas.apply {
                                    batchInsert(data) {
                                        this[typeid] = type
                                        this[date] = it.date

                                        this[average] = it.average
                                        this[low] = it.lowest
                                        this[high] = it.highest
                                        this[orders] = it.orderCount
                                        this[volume] = it.volume
                                    }
                                    Unit
                                }
                            }
                        }
                        val doneNow = done.incrementAndGet()
                        println("$doneNow / $original done (${100 * doneNow / original}%)")
                        //delay((800 + doneNow % 300).toLong())
                    }
                }.awaitAll()
            }
        }

        client.close()
    }
}
