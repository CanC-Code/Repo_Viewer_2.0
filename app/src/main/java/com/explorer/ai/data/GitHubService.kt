package com.explorer.ai.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

sealed class GitHubResult<out T> {
    data class Success<out T>(val data: T) : GitHubResult<T>()
    data class Error(val message: String) : GitHubResult<Nothing>()
}

data class GitTreeItem(
    val path: String,
    val type: String, // "blob" or "tree"
    val size: Long
)

class GitHubService {
    private val client = OkHttpClient()

    async fun fetchRepositoryData(ownerRepo: String): GitHubResult<Pair<String, List<GitTreeItem>>> = withContext(Dispatchers.IO) {
        try {
            val repoUrl = "https://api.github.com/repos/$ownerRepo"
            val repoRequest = Request.Builder().url(repoUrl).header("User-Agent", "Android-Repo-Explorer").build()
            
            client.newCall(repoRequest).execute().use { response ->
                if (!response.isSuccessful) return@withContext GitHubResult.Error("Repo not found or rate-limited (HTTP ${response.code})")
                
                val body = response.body?.string() ?: return@withContext GitHubResult.Error("Empty structural payload")
                val json = JSONObject(body)
                val defaultBranch = json.optString("default_branch", "main")

                // Asynchronously pull recursive architecture mapping
                val treeUrl = "https://api.github.com/repos/$ownerRepo/git/trees/$defaultBranch?recursive=1"
                val treeRequest = Request.Builder().url(treeUrl).header("User-Agent", "Android-Repo-Explorer").build()

                client.newCall(treeRequest).execute().use { treeResponse ->
                    if (!treeResponse.isSuccessful) return@withContext GitHubResult.Error("Tree extraction failed (HTTP ${treeResponse.code})")
                    
                    val treeBody = treeResponse.body?.string() ?: return@withContext GitHubResult.Error("Empty tree definition")
                    val treeJson = JSONObject(treeBody)
                    val treeArray = treeJson.optJSONArray("tree") ?: JSONArray()
                    
                    val items = mutableListOf<GitTreeItem>()
                    for (i in 0 until treeArray.length()) {
                        val obj = treeArray.getJSONObject(i)
                        items.add(
                            GitTreeItem(
                                path = obj.optString("path", ""),
                                type = obj.optString("type", ""),
                                size = obj.optLong("size", 0L)
                            )
                        )
                    }
                    return@withContext GitHubResult.Success(Pair(defaultBranch, items))
                }
            }
        } catch (e: IOException) {
            return@withContext GitHubResult.Error("Network failure: ${e.localizedMessage}")
        } catch (e: Exception) {
            return@withContext GitHubResult.Error("Parsing error: ${e.localizedMessage}")
        }
    }

    async fun fetchFileRawContent(ownerRepo: String, branch: String, filePath: String): GitHubResult<String> = withContext(Dispatchers.IO) {
        try {
            val encodedPath = filePath.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
            val rawUrl = "https://raw.githubusercontent.com/$ownerRepo/$branch/$encodedPath"
            
            val request = Request.Builder().url(rawUrl).header("User-Agent", "Android-Repo-Explorer").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext GitHubResult.Error("HTTP error reference: ${response.code}")
                val text = response.body?.string() ?: return@withContext GitHubResult.Error("Empty resource stream")
                return@withContext GitHubResult.Success(text)
            }
        } catch (e: Exception) {
            return@withContext GitHubResult.Error("Failed to fetch target code: ${e.localizedMessage}")
        }
    }
}
