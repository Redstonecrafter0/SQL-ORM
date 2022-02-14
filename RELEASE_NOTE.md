SQL-ORM Release {{ VERSION }}

# SQL-ORM Release {{ VERSION }}

Any version of Java from 8 to 17 is supported.
In order to use any SQL Database you have to add the dependency of it.
SQLite and MySQL/MariaDB are tested.

In order to use GitHub's Maven Repository you need to authenticate using the `settings.xml` file.
Any account would work as this project is public.

You can use jitpack as well.

```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/Redstonecrafter0/SQL-ORM</url>
</repository>

<dependency>
    <groupId>net.redstonecraft</groupId>
    <artifactId>sql-orm</artifactId>
    <version>{{ VERSION }}</version>
</dependency>
```
