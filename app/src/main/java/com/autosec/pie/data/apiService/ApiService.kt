package com.autosec.pie.data.apiService

import com.autosec.pie.domain.model.CloudCommandModel
import com.autosec.pie.domain.model.CloudCommandsListDto
import com.autosec.pie.domain.model.CloudPackageListDTO
import com.autosec.pie.domain.model.GenericResponseDTO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class ApiServiceImpl(
    private var client : HTTPClientService
) : ApiService {
    override suspend fun getCloudCommandsList(): Flow<GenericResponseDTO<CloudCommandsListDto>> = flow {
        emit(client.sendRequest(MainRouter.GetCloudCommandsList))
    }

    override suspend fun getMoreCloudCommandsList(cursor: String): Flow<GenericResponseDTO<CloudCommandsListDto>> = flow {
        emit(client.sendRequest(MainRouter.GetMoreCloudCommandsList(cursor)))
    }

    override suspend fun searchCloudCommands(query: String): Flow<GenericResponseDTO<CloudCommandsListDto>> = flow {
        emit(client.sendRequest(MainRouter.SearchCloudCommands(query)))
    }

    override suspend fun getPackages(query: String): Flow<GenericResponseDTO<CloudPackageListDTO>> = flow {
        emit(client.sendRequest(MainRouter.GetPackages(query)))
    }

    override suspend fun getMorePackages(query: String,cursor: String): Flow<GenericResponseDTO<CloudPackageListDTO>> = flow {
        emit(client.sendRequest(MainRouter.GetMorePackages(query, cursor)))
    }





}


interface ApiService {

    suspend fun getCloudCommandsList(): Flow<GenericResponseDTO<CloudCommandsListDto>>
    suspend fun searchCloudCommands(query: String): Flow<GenericResponseDTO<CloudCommandsListDto>>
    suspend fun getMoreCloudCommandsList(cursor: String): Flow<GenericResponseDTO<CloudCommandsListDto>>
    suspend fun getPackages(query: String): Flow<GenericResponseDTO<CloudPackageListDTO>>
    suspend fun getMorePackages(query: String,cursor: String): Flow<GenericResponseDTO<CloudPackageListDTO>>
}