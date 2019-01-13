package com.rnett.market_history_data

import com.rnett.exposedgson.ExposedGSON
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.LongEntity
import org.jetbrains.exposed.dao.LongEntityClass
import org.jetbrains.exposed.dao.LongIdTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.transactions.transaction

object historydatas : LongIdTable("historydata", "id"){
    val idCol = long("id").autoIncrement().primaryKey()

    val typeid = integer("typeid")
    val date = varchar("date", 10)

    val average = double("average")
    val low = double("low")
    val high = double("high")
    val orders = long("orders")
    val volume = long("volume")

    init{
        uniqueIndex(typeid, date)
    }
}

class DBHistoryData(id: EntityID<Long>) : LongEntity(id){

    companion object : LongEntityClass<DBHistoryData>(historydatas){

        operator fun get(typeId: Int) = find{ historydatas.typeid eq typeId }.toList().sortedByDescending { it.date }

        operator fun get(typeId: Int, date: String) = find{ historydatas.typeid eq typeId and(historydatas.date eq date) }.first()

        operator fun get(date: String) = find{ historydatas.date eq date }.associateBy { it.typeId }

        operator fun get(date: String, typeIds: List<Int>) = find{ historydatas.date eq date and(historydatas.typeid inList typeIds) }.associateBy { it.typeId }

        fun allByType() = all().groupBy { it.typeId }.mapValues { it.value.sortedByDescending { it.date } }
        fun allByDate() = all().groupBy { it.date }
            .mapValues { it.value.associateBy { it.typeId } }
            .toSortedMap(Comparator { o1, o2 -> -1 * o1.compareTo(o2)})

    }

    @ExposedGSON.Ignore
    private val idCol by historydatas.idCol

    val typeId by historydatas.typeid
    val date by historydatas.date

    val average by historydatas.average
    val low by historydatas.low
    val high by historydatas.high
    val orders by historydatas.orders
    val volume by historydatas.volume

    fun toData() = HistoryData(
        typeId,
        date,
        average,
        low,
        high,
        orders,
        volume
    )
}

fun Iterable<DBHistoryData>.toData() = map{it.toData()}

@Serializable
data class HistoryData(
    val typeId: Int,
    val date: String,
    val average: Double,
    val low: Double,
    val high: Double,
    val orders: Long,
    val volume: Long
){
    fun toCSV() = "$typeId,$date,$average,$low,$high,$orders,$volume"

    companion object {
        val csvHeader = "typeid,date,average,low,high,orders,volume"

        operator fun get(typeId: Int) = transaction{
            DBHistoryData.find { historydatas.typeid eq typeId }.toData().sortedByDescending { it.date }
        }

        operator fun get(typeId: Int, date: String) = transaction{
            DBHistoryData.find { historydatas.typeid eq typeId and (historydatas.date eq date) }.first().toData() }

        operator fun get(date: String) = transaction{
            DBHistoryData.find { historydatas.date eq date }.toData().associateBy { it.typeId }
        }

        operator fun get(date: String, typeIds: List<Int>) = transaction {
            DBHistoryData.find { historydatas.date eq date and (historydatas.typeid inList typeIds) }
                .toData().associateBy { it.typeId }
        }


        fun allByType() = transaction {
            DBHistoryData.all().groupBy { it.typeId }.mapValues { it.value.toData().sortedByDescending { it.date } }
        }

        fun allByDate() = transaction {
            DBHistoryData.all().groupBy { it.date }
                .mapValues { it.value.toData().associateBy { it.typeId } }
                .toSortedMap(Comparator { o1, o2 -> -1 * o1.compareTo(o2) })
        }
    }

}
