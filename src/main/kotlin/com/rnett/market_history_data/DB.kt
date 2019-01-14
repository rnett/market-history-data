package com.rnett.market_history_data

import com.rnett.core.PooledDBConnection
import org.jetbrains.exposed.sql.Database
import java.io.File

private fun getUrl(useLocalhost: Boolean) = File("dbconnection").readLines()[if (useLocalhost) 1 else 0]

fun connectToDB(useLocalhost: Boolean = false) =
    Database.connect(PooledDBConnection.connect(getUrl(useLocalhost)))
