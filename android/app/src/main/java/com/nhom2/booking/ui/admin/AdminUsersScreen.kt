package com.nhom2.booking.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nhom2.booking.R
import com.nhom2.booking.data.api.AdminApi
import com.nhom2.booking.data.model.UserBody
import com.nhom2.booking.data.model.UserProfile
import com.nhom2.booking.data.model.UserRole
import com.nhom2.booking.data.network.ApiException
import com.nhom2.booking.data.network.apiCall
import com.nhom2.booking.domain.isStrongPassword
import com.nhom2.booking.domain.isValidEmail
import com.nhom2.booking.ui.CollectUiEvents
import com.nhom2.booking.ui.EventViewModel
import com.nhom2.booking.ui.LoadState
import com.nhom2.booking.ui.UiText
import com.nhom2.booking.ui.asString
import com.nhom2.booking.ui.components.AppTopBar
import com.nhom2.booking.ui.components.ConfirmDialog
import com.nhom2.booking.ui.components.EmptyState
import com.nhom2.booking.ui.components.ErrorBox
import com.nhom2.booking.ui.components.LoadingBox
import com.nhom2.booking.ui.components.PasswordTextField
import com.nhom2.booking.ui.components.SectionCard
import com.nhom2.booking.ui.components.StatusChip
import com.nhom2.booking.ui.format.StatusNeutral
import com.nhom2.booking.ui.format.StatusPending
import com.nhom2.booking.ui.toUiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UserDraft(val username: String, val email: String, val password: String, val role: String) {
    /** A new account needs a password; on edit an empty one keeps the current password. */
    fun validationError(isNew: Boolean): UiText? = when {
        username.isBlank() -> UiText.Res(R.string.auth_username_required)
        !isValidEmail(email) -> UiText.Res(R.string.auth_email_invalid)
        isNew && password.isEmpty() -> UiText.Res(R.string.auth_password_required)
        password.isNotEmpty() && !isStrongPassword(password) -> UiText.Res(R.string.auth_password_policy)
        else -> null
    }
}

class AdminUsersViewModel(
    private val adminApi: AdminApi,
    /** The signed-in admin, who must not delete their own account from here. */
    val currentUsername: String,
) : EventViewModel() {

    private val _users = MutableStateFlow<LoadState<List<UserProfile>>>(LoadState.Loading)
    val users: StateFlow<LoadState<List<UserProfile>>> = _users.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            if (_users.value !is LoadState.Success) _users.value = LoadState.Loading
            try {
                _users.value = LoadState.Success(apiCall { adminApi.getUsers() }.sortedBy { it.username.lowercase() })
            } catch (e: ApiException) {
                if (_users.value is LoadState.Success) {
                    showError(e, R.string.admin_list_load_failed)
                } else {
                    _users.value = LoadState.Error(e.toUiText(UiText.Res(R.string.admin_list_load_failed)))
                }
            }
        }
    }

    fun save(existing: UserProfile?, draft: UserDraft) = mutate(R.string.admin_user_saved) {
        val body = UserBody(
            id = existing?.id,
            username = draft.username.trim(),
            email = draft.email.trim(),
            password = draft.password.ifEmpty { null },
            role = draft.role,
        )
        val id = existing?.id
        if (id == null) adminApi.createUser(body) else adminApi.updateUser(id, body)
    }

    fun delete(user: UserProfile) {
        val id = user.id ?: return
        mutate(R.string.admin_user_deleted) { adminApi.deleteUser(id) }
    }

    private fun mutate(successRes: Int, action: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                apiCall { action() }
                showMessage(successRes)
                load()
            } catch (e: ApiException) {
                showError(e)
            } finally {
                _busy.value = false
            }
        }
    }
}

@Composable
fun AdminUsersScreen(viewModel: AdminUsersViewModel, onMessage: (UiText) -> Unit, onBack: () -> Unit) {
    val state by viewModel.users.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<UserProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<UserProfile?>(null) }
    CollectUiEvents(viewModel.events, onMessage, onCompleted = {})

    Scaffold(
        topBar = { AppTopBar(stringResource(R.string.admin_users_title), onBack = onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { creating = true }) {
                Icon(Icons.Filled.PersonAdd, contentDescription = stringResource(R.string.admin_add_user))
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            when (val current = state) {
                LoadState.Loading -> LoadingBox()
                is LoadState.Error -> ErrorBox(current.message, onRetry = viewModel::load)
                is LoadState.Success -> {
                    val needle = query.trim().lowercase()
                    val users = current.data.filter {
                        needle.isEmpty() || it.username.lowercase().contains(needle) || it.email.orEmpty().lowercase().contains(needle)
                    }
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text(stringResource(R.string.admin_search_users)) },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        if (users.isEmpty()) item { EmptyState(Icons.Filled.Group, stringResource(R.string.admin_list_empty), body = null) }
                        items(users, key = { it.id ?: it.username.hashCode().toLong() }) { user ->
                            UserRow(
                                user = user,
                                canDelete = user.username != viewModel.currentUsername,
                                onEdit = { editing = user },
                                onDelete = { deleting = user },
                            )
                        }
                    }
                }
            }
        }
    }

    if (creating || editing != null) {
        UserDialog(
            user = editing,
            onSave = { draft ->
                viewModel.save(editing, draft)
                creating = false
                editing = null
            },
            onDismiss = {
                creating = false
                editing = null
            },
        )
    }
    deleting?.let { user ->
        ConfirmDialog(
            title = stringResource(R.string.delete),
            text = stringResource(R.string.admin_delete_user_confirm, user.username),
            confirmLabel = stringResource(R.string.delete),
            onConfirm = {
                viewModel.delete(user)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
private fun UserRow(user: UserProfile, canDelete: Boolean, onEdit: () -> Unit, onDelete: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(user.username, fontWeight = FontWeight.Bold)
                Text(user.email.orEmpty(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val isAdmin = user.role == UserRole.ADMIN
                StatusChip(
                    stringResource(if (isAdmin) R.string.role_admin else R.string.role_user),
                    if (isAdmin) StatusPending else StatusNeutral,
                )
            }
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit)) }
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun UserDialog(user: UserProfile?, onSave: (UserDraft) -> Unit, onDismiss: () -> Unit) {
    var draft by remember {
        mutableStateOf(UserDraft(user?.username.orEmpty(), user?.email.orEmpty(), "", user?.role ?: UserRole.USER))
    }
    val error = draft.validationError(isNew = user == null)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (user == null) R.string.admin_add_user else R.string.admin_edit_user)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.username,
                    onValueChange = { draft = draft.copy(username = it) },
                    label = { Text(stringResource(R.string.auth_username)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.email,
                    onValueChange = { draft = draft.copy(email = it) },
                    label = { Text(stringResource(R.string.auth_email)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PasswordTextField(
                    value = draft.password,
                    onValueChange = { draft = draft.copy(password = it) },
                    label = stringResource(if (user == null) R.string.auth_password else R.string.admin_new_password_optional),
                    error = null,
                )
                listOf(UserRole.USER to R.string.role_user, UserRole.ADMIN to R.string.role_admin).forEach { (role, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = draft.role == role, onClick = { draft = draft.copy(role = role) }),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = draft.role == role, onClick = null)
                        Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (error != null) Text(error.asString(), color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        },
        confirmButton = { Button(onClick = { onSave(draft) }, enabled = error == null) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
