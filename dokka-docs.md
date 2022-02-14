# Module SQL-ORM

This project aims to simplify database access by leveraging the potential
of Kotlin's data classes.

First create your model with a dataclass.

```kotlin
data class TestModel(
    @PrimaryKey var id: Long = -1, // The primary key is marked with its annotation and initialized with an unused value. It will change once added.
    @SQLChar(5) var char: String,
    @SQLVarchar(5) var varchar: String,
    var text: String, // Use the SQLChar or SQLVarChar annotation to use those types. If omitted it will be Text. No effect on SQLite.
    var int: Int,
    var bigInt: Long,
    var blob: ByteArray?, // Nullability is adopted
    var boolean: Boolean,
    var float: Float,
    var double: Double
) { // When using a ByteArray you must autogenerate those functions. 
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TestModel
        if (id != other.id) return false
        if (char != other.char) return false
        if (varchar != other.varchar) return false
        if (text != other.text) return false
        if (int != other.int) return false
        if (bigInt != other.bigInt) return false
        if (blob != null) {
            if (other.blob == null) return false
            if (!blob.contentEquals(other.blob)) return false
        } else if (other.blob != null) return false
        if (boolean != other.boolean) return false
        if (float != other.float) return false
        if (double != other.double) return false
        return true
    }
    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + char.hashCode()
        result = 31 * result + varchar.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + int
        result = 31 * result + bigInt.hashCode()
        result = 31 * result + (blob?.contentHashCode() ?: 0)
        result = 31 * result + boolean.hashCode()
        result = 31 * result + float.hashCode()
        result = 31 * result + double.hashCode()
        return result
    }
}
```

If that's done you can initialize your db connection.

```kotlin
val db = Database(
    DriverManager.getConnection("jdbc:sqlite::memory:"), // Use the standard DriverManager to create the underlying connection.
    SQLiteTranslator // Use the Translator object you need for creation of the SQL queries.
)

db.makeCurrent() // Use this call to access the db directly by the Database companion object.
// Else replace `Database` with `db` in the following code.
```

Next, create that table.

```kotlin
Database.createTable<TestModel>()
```

Add your first entry.  
When a new entity is inserted the primary key you defined will automatically be updated.

```kotlin
val entity = TestModel(
    char = "chars",
    varchar = "char",
    text = "text",
    int = 5,
    bigInt = Int.MAX_VALUE + 10L,
    blob = byteArrayOf(4, 2, 0),
    boolean = true,
    float = .2F,
    double = .2
)

Database += entity // It can be done like this, but it's a better idea to use `set` and `update` directly.
Database.set(entity) // Inserts a new entity.
Database.update(entity) // updates an existing entity.
```

Querying is the biggest reason for writing this library.
It uses Kotlin's reference for a property.

Here are some examples.

```kotlin
val entity = Database.query<TestModel> {
    TestModel::id eq 5L
}.first()

val entities = Database.query<TestModel> {
    ((TestModel::id notEq 5L) and (TestModel::id less 10L)) 
    or ((TestModel::text eq "text") and (TestModel::bigInt eq 50L))
}
```

All valid operations are:
- eq
- notEq
- greater
- less
- greaterEq
- lessEq
- between
- notBetween
- like
- notLike
- isIn
- notIn
- and
- or

Count, sum and avg use the same queries.

```kotlin
val countGreaterFive = Database.count<TestModel> { TestModel::id greater 5L }
val countAll = Database.count<TestModel>() // When omitting the query everything will apply. This works also for other methods using queries.
val sum = Database.sum<TestModel>(TestModel::int) { TestModel::id greater 5L }
val avg = Database.avg<TestModel>(TestModel::int)
```
