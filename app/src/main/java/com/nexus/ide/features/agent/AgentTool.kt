package com.nexus.ide.features.agent

import org.json.JSONArray
import org.json.JSONObject

/**
 * Every action the coding agent can request. Each tool maps 1:1 to
 * an OpenAI-style function definition sent in the API request, and to
 * an executor in [AgentEngine].
 *
 * Design rules:
 *  - Tools are intentionally coarse (read_file, not read_line).
 *  - Destructive tools (write_file, run_command, delete_file) require
 *    explicit user approval before execution — the ViewModel holds this gate.
 *  - No tool ever touches files outside the workspace root.
 */
sealed class AgentTool(
    val name: String,
    val description: String,
    val requiresApproval: Boolean
) {
    object ReadFile : AgentTool(
        name = "read_file",
        description = "Read the full text content of a file in the workspace. Returns file contents as a string.",
        requiresApproval = false
    )

    object WriteFile : AgentTool(
        name = "write_file",
        description = "Create or overwrite a file with the given content. Creates parent directories automatically.",
        requiresApproval = true
    )

    object ListDirectory : AgentTool(
        name = "list_directory",
        description = "List files and directories at the given path. Returns a JSON array of entries with name, type, and size.",
        requiresApproval = false
    )

    object SearchFiles : AgentTool(
        name = "search_files",
        description = "Search for a regex pattern across all files in the workspace. Returns matching lines with file paths.",
        requiresApproval = false
    )

    object RunCommand : AgentTool(
        name = "run_command",
        description = "Execute a shell command in the workspace root via Termux. Returns stdout and stderr.",
        requiresApproval = true
    )

    object DeleteFile : AgentTool(
        name = "delete_file",
        description = "Delete a file or directory (recursively) in the workspace.",
        requiresApproval = true
    )

    object RenameFile : AgentTool(
        name = "rename_file",
        description = "Rename or move a file within the workspace.",
        requiresApproval = true
    )

    companion object {
        val all: List<AgentTool> = listOf(
            ReadFile, WriteFile, ListDirectory, SearchFiles,
            RunCommand, DeleteFile, RenameFile
        )

        fun byName(name: String): AgentTool? = all.firstOrNull { it.name == name }

        /** Build the tools array for the OpenAI API request. */
        fun toApiJson(): JSONArray {
            val arr = JSONArray()
            all.forEach { tool ->
                arr.put(JSONObject().apply {
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", parametersFor(tool))
                    })
                })
            }
            return arr
        }

        private fun parametersFor(tool: AgentTool): JSONObject = when (tool) {
            is ReadFile -> JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Relative path to the file from workspace root")
                    })
                })
                put("required", JSONArray().put("path"))
            }
            is WriteFile -> JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Relative path to write to")
                    })
                    put("content", JSONObject().apply {
                        put("type", "string")
                        put("description", "Full file content to write")
                    })
                })
                put("required", JSONArray().put("path").put("content"))
            }
            is ListDirectory -> JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Relative path to list, or '.' for workspace root")
                    })
                })
                put("required", JSONArray().put("path"))
            }
            is SearchFiles -> JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("pattern", JSONObject().apply {
                        put("type", "string")
                        put("description", "Regex pattern to search for")
                    })
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Directory to search in, or '.' for entire workspace")
                    })
                })
                put("required", JSONArray().put("pattern"))
            }
            is RunCommand -> JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("command", JSONObject().apply {
                        put("type", "string")
                        put("description", "Shell command to execute")
                    })
                })
                put("required", JSONArray().put("command"))
            }
            is DeleteFile -> JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("path", JSONObject().apply {
                        put("type", "string")
                        put("description", "Relative path of file or directory to delete")
                    })
                })
                put("required", JSONArray().put("path"))
            }
            is RenameFile -> JSONObject().apply {
                put("type", "object")
                put("properties", JSONObject().apply {
                    put("from", JSONObject().apply {
                        put("type", "string")
                        put("description", "Current relative path")
                    })
                    put("to", JSONObject().apply {
                        put("type", "string")
                        put("description", "New relative path")
                    })
                })
                put("required", JSONArray().put("from").put("to"))
            }
        }
    }
}
