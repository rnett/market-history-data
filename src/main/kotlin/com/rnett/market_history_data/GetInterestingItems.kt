package com.rnett.market_history_data

import com.google.gson.Gson
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.div
import org.jetbrains.exposed.sql.SqlExpressionBuilder.minus
import org.jetbrains.exposed.sql.SqlExpressionBuilder.times
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

inline fun <T> Iterable<T>.averageBy(field: (T) -> Double) = sumByDouble(field) / count()
data class Type(val typeId: Int = -1, val typeName: String = "invalid")
object GetInterestingItems {
    @JvmStatic
    fun main(args: Array<String>) {
        connectToDB()

        //TODO better way of doing this.  varience * volume instead of percent?

        val minTotalIskPerDay = 50_000_000.0
        val minOrders = 5
        val minStd = 20

        val sb = StringBuilder()

        val types = mutableSetOf<Int>()

        transaction {
            historydatas.run {
                val isk = (average.avg() * volume.avg()).alias("isk")
                val orders = orders.avg().alias("orders")
                val variance = ((average.max() - average.min()) / average.avg().castTo(DoubleColumnType()))
                    .alias("variance")

                slice(
                    typeid,
                    isk,
                    orders,
                    variance
                ).selectAll().groupBy(typeid).forEach {
                    val str = "${usefulTypes[it[typeid]]} [${it[typeid]}]: " +
                            "${it[isk]!!.toLong() / 1_000_000} M isk/day, " +
                            "${it[orders]!!.toInt()} orders, " +
                            "${(it[variance]!! * 100).toInt()}% variance"
                    print(str)
                    sb.append(str)
                    print(" ".repeat(135 - str.length))
                    sb.append(" ".repeat(135 - str.length))

                    if (it[isk]!! > minTotalIskPerDay.toBigDecimal() && it[orders]!! > minOrders.toBigDecimal() && it[variance]!! > minStd / 100) {
                        println("Passed")
                        sb.appendln("Passed")
                        types += it[typeid]
                    } else {
                        println("Failed")
                        sb.appendln("Failed")
                    }


                }
            }
        }

        File("log.txt").writeText(sb.toString())

        println("${types.size} interesting types")

        File(args[0]).writeText(
            Gson().toJson(types)
        )

    }
}