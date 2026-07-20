package com.sekiguchi.salesapp

/** 社内情報・固有名詞の混入チェック。意思ではなく仕組みで担保する。 */
object Leak {

    private val companyPattern = Regex("株式会社|\\(株\\)|（株）|有限会社|合同会社|㈱")
    private val modelPattern = Regex("[A-Za-z]{2,}-?[0-9]{3,}")

    fun check(text: String): List<String> {
        val hits = ArrayList<String>()
        companyPattern.findAll(text).forEach { if (!hits.contains(it.value)) hits.add(it.value) }
        modelPattern.findAll(text).forEach { if (!hits.contains(it.value)) hits.add(it.value) }
        return hits
    }
}
