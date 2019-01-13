package com.rnett.market_history_data

fun readResource(resourceFile: String) =
    Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceFile).bufferedReader().use{ it.readText() }