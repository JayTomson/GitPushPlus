package com.example.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.HTTP
import retrofit2.http.Path
import retrofit2.http.Query

@JsonClass(generateAdapter = true)
data class GithubRepo(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "full_name") val fullName: String,
    @Json(name = "private") val isPrivate: Boolean,
    @Json(name = "default_branch") val defaultBranch: String?
)

@JsonClass(generateAdapter = true)
data class CreateRepoRequest(
    @Json(name = "name") val name: String,
    @Json(name = "private") val isPrivate: Boolean,
    @Json(name = "auto_init") val autoInit: Boolean = true
)

@JsonClass(generateAdapter = true)
data class GithubBranch(
    @Json(name = "name") val name: String,
    @Json(name = "commit") val commit: BranchCommitInfo? = null
)

@JsonClass(generateAdapter = true)
data class BranchCommitInfo(
    @Json(name = "sha") val sha: String,
    @Json(name = "url") val url: String? = null
)

@JsonClass(generateAdapter = true)
data class GithubCommit(
    @Json(name = "sha") val sha: String,
    @Json(name = "commit") val commitDetail: CommitDetail
)

@JsonClass(generateAdapter = true)
data class CommitDetail(
    @Json(name = "message") val message: String,
    @Json(name = "author") val author: CommitAuthor? = null
)

@JsonClass(generateAdapter = true)
data class CommitAuthor(
    @Json(name = "name") val name: String,
    @Json(name = "date") val date: String
)

@JsonClass(generateAdapter = true)
data class MergeRequest(
    @Json(name = "base") val base: String,
    @Json(name = "head") val head: String,
    @Json(name = "commit_message") val commitMessage: String
)

@JsonClass(generateAdapter = true)
data class MergeResponse(
    @Json(name = "sha") val sha: String?,
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateBranchRefRequest(
    @Json(name = "ref") val ref: String,
    @Json(name = "sha") val sha: String
)

@JsonClass(generateAdapter = true)
data class FileResponse(
    @Json(name = "sha") val sha: String?,
    @Json(name = "name") val name: String?,
    @Json(name = "path") val path: String?,
    @Json(name = "content") val content: String? = null,
    @Json(name = "encoding") val encoding: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateOrUpdateFileRequest(
    @Json(name = "message") val message: String,
    @Json(name = "content") val content: String,
    @Json(name = "branch") val branch: String,
    @Json(name = "sha") val sha: String? = null
)

@JsonClass(generateAdapter = true)
data class DeleteFileRequest(
    @Json(name = "message") val message: String,
    @Json(name = "sha") val sha: String,
    @Json(name = "branch") val branch: String
)

@JsonClass(generateAdapter = true)
data class GithubUser(
    @Json(name = "login") val login: String
)

interface GithubApiService {
    @GET("user")
    suspend fun getUserInfo(@Header("Authorization") token: String): Response<GithubUser>

    @GET("user/repos")
    suspend fun getMyRepos(
        @Header("Authorization") token: String,
        @Query("sort") sort: String = "updated",
        @Query("per_page") perPage: Int = 100
    ): List<GithubRepo>

    @POST("user/repos")
    suspend fun createRepo(
        @Header("Authorization") token: String,
        @Body request: CreateRepoRequest
    ): GithubRepo

    @GET("repos/{owner}/{repo}/branches")
    suspend fun getBranches(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GithubBranch>

    @GET("repos/{owner}/{repo}/commits")
    suspend fun getCommits(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("sha") branch: String? = null,
        @Query("per_page") perPage: Int = 50
    ): List<GithubCommit>

    @POST("repos/{owner}/{repo}/merges")
    suspend fun mergeBranch(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: MergeRequest
    ): Response<MergeResponse>

    @POST("repos/{owner}/{repo}/git/refs")
    suspend fun createBranchRef(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Body request: CreateBranchRefRequest
    ): Response<Any>

    @GET("repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Query("ref") branch: String
    ): Response<FileResponse>

    @PUT("repos/{owner}/{repo}/contents/{path}")
    suspend fun createOrUpdateFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: CreateOrUpdateFileRequest
    ): Response<FileResponse>

    @HTTP(method = "DELETE", path = "repos/{owner}/{repo}/contents/{path}", hasBody = true)
    suspend fun deleteFile(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("path") path: String,
        @Body request: DeleteFileRequest
    ): Response<FileResponse>

    @GET("repos/{owner}/{repo}/git/trees/{tree_sha}")
    suspend fun getGitTree(
        @Header("Authorization") token: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path("tree_sha") treeSha: String,
        @Query("recursive") recursive: Int = 1
    ): Response<GitTreeResponse>
}

@JsonClass(generateAdapter = true)
data class GitTreeResponse(
    @Json(name = "sha") val sha: String,
    @Json(name = "tree") val tree: List<GitTreeEntry>,
    @Json(name = "truncated") val truncated: Boolean
)

@JsonClass(generateAdapter = true)
data class GitTreeEntry(
    @Json(name = "path") val path: String,
    @Json(name = "mode") val mode: String,
    @Json(name = "type") val type: String, // "blob" (file) or "tree" (dir)
    @Json(name = "sha") val sha: String,
    @Json(name = "size") val size: Long? = null,
    @Json(name = "url") val url: String? = null
)
