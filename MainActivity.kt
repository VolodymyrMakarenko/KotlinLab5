
package softserve.academy.mylist

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.room.*
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import softserve.academy.mylist.ui.theme.MyListTheme
import androidx.lifecycle.viewmodel.compose.viewModel


// Entity
@Entity(tableName = "shopping_items")
data class ShoppingItem(
    val name: String,
    val isBought: Boolean = false,
    @PrimaryKey(autoGenerate = true) val id: Int = 0
)

// DAO
@Dao
interface ShoppingDao {
    @Query("SELECT * FROM shopping_items ORDER BY id DESC")
    fun getAllItems(): List<ShoppingItem>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertItem(item: ShoppingItem)

    @Update
    fun updateItem(item: ShoppingItem)

    @Delete
    fun deleteItem(item: ShoppingItem)
}

@Database(entities = [ShoppingItem::class], version = 1)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile private var INSTANCE: ShoppingDatabase? = null

        fun getInstance(context: Context): ShoppingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ShoppingDatabase::class.java,
                    "shopping_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class ShoppingListViewModel(application: Application) : AndroidViewModel(application) {
    private val dao: ShoppingDao = ShoppingDatabase.getInstance(application).shoppingDao()
    private val _shoppingList = mutableStateListOf<ShoppingItem>()
    val shoppingList: List<ShoppingItem> get() = _shoppingList

    init { loadShoppingList() }

    fun loadShoppingList() {
        viewModelScope.launch(Dispatchers.IO) {
            val items = dao.getAllItems()
            _shoppingList.clear()
            _shoppingList.addAll(items)
        }
    }

    fun addItem(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertItem(ShoppingItem(name = name))
            loadShoppingList()
        }
    }

    fun toggleBought(index: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = _shoppingList[index]
            dao.updateItem(item.copy(isBought = !item.isBought))
            loadShoppingList()
        }
    }

    fun deleteItem(item: ShoppingItem) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteItem(item)
            loadShoppingList()
        }
    }

    fun editItem(item: ShoppingItem, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateItem(item.copy(name = newName))
            loadShoppingList()
        }
    }

    fun refresh(onDone: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            loadShoppingList()
            onDone()
        }
    }
}

class ShoppingListViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ShoppingListViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ShoppingListViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


@Composable
fun AddItemButton(addItem: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    Column {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(stringResource(R.string.add_item)) }
        )
        Button(onClick = {
            if (text.isNotBlank()) {
                addItem(text)
                text = ""
            }
        }) {
            Text(stringResource(R.string.add))
        }
    }
}

@Composable
fun ShoppingItemCard(
    item: ShoppingItem,
    onToggleBought: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp).background(Color.LightGray, MaterialTheme.shapes.large).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = item.isBought, onCheckedChange = { onToggleBought() })
        Text(text = item.name, modifier = Modifier.weight(1f), fontSize = 18.sp)
        IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit") }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
    }
}

@Composable
fun EditItemDialog(
    item: ShoppingItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue(item.name)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = { onSave(text.text) }) { Text("Save") }
        },
        dismissButton = {
            Button(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Edit Item") },
        text = {
            OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Item name") })
        }
    )
}

@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel = viewModel(factory = ShoppingListViewModelFactory(LocalContext.current.applicationContext as Application))) {
    var editingItem by remember { mutableStateOf<ShoppingItem?>(null) }
    val refreshing = remember { mutableStateOf(false) }

    val boughtCount = viewModel.shoppingList.count { it.isBought }
    val totalCount = viewModel.shoppingList.size

    SwipeRefresh(state = rememberSwipeRefreshState(refreshing.value), onRefresh = {
        refreshing.value = true
        viewModel.refresh { refreshing.value = false }
    }) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text("Куплено: $boughtCount з $totalCount", fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            AddItemButton { viewModel.addItem(it) }
            Spacer(modifier = Modifier.height(16.dp))
            LazyColumn {
                itemsIndexed(viewModel.shoppingList) { index, item ->
                    ShoppingItemCard(
                        item = item,
                        onToggleBought = { viewModel.toggleBought(index) },
                        onEdit = { editingItem = item },
                        onDelete = { viewModel.deleteItem(item) }
                    )
                }
            }
        }
    }

    editingItem?.let { item ->
        EditItemDialog(
            item = item,
            onDismiss = { editingItem = null },
            onSave = {
                viewModel.editItem(item, it)
                editingItem = null
            }
        )
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyListTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Surface(modifier = Modifier.padding(innerPadding)) {
                        ShoppingListScreen()
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PreviewScreen() {
    MyListTheme {
        ShoppingListScreen()
    }
}
