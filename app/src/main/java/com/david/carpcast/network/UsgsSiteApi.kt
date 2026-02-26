package com.david.carpcast.network

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.QueryMap

interface UsgsSiteApi {
    // Flexible site search using query map (USGS site service)
    @GET("nwis/site/")
    suspend fun searchSites(@QueryMap params: Map<String, String>): Response<ResponseBody>
}
