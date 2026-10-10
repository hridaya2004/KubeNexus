package dev.hridaya.kubenexus.data.source.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.hridaya.kubenexus.data.source.local.dao.APIResourceDao
import dev.hridaya.kubenexus.data.source.local.dao.ClusterDao
import dev.hridaya.kubenexus.data.source.local.dao.ExplainedResourceDao
import dev.hridaya.kubenexus.data.source.local.dao.NamespaceDao
import dev.hridaya.kubenexus.data.source.local.dao.OpenApiSchemaDao
import dev.hridaya.kubenexus.data.source.local.dao.ResourceCacheDao
import dev.hridaya.kubenexus.data.source.local.entity.APIResourceEntity
import dev.hridaya.kubenexus.data.source.local.entity.CachedResourceEntity
import dev.hridaya.kubenexus.data.source.local.entity.ClusterEntity
import dev.hridaya.kubenexus.data.source.local.entity.ExplainedResourceEntity
import dev.hridaya.kubenexus.data.source.local.entity.NamespaceEntity
import dev.hridaya.kubenexus.data.source.local.entity.OpenApiSchemaEntity
import dev.hridaya.kubenexus.data.source.local.entity.SyncMetadataEntity

@Database(
    entities = [
        ClusterEntity::class,
        NamespaceEntity::class,
        SyncMetadataEntity::class,
        APIResourceEntity::class,
        ExplainedResourceEntity::class,
        OpenApiSchemaEntity::class,
        CachedResourceEntity::class,
    ],
    version = 8,
    exportSchema = true,
    // AutoMigration(5, 6) is not possible: schema JSON was never exported for
    // v5. From here on, exported schemas let future bumps declare auto-migrations.
    autoMigrations = [],
)
abstract class KubeNexusDatabase : RoomDatabase() {

    abstract fun clusterDao(): ClusterDao
    abstract fun namespaceDao(): NamespaceDao
    abstract fun apiResourceDao(): APIResourceDao
    abstract fun explainedResourceDao(): ExplainedResourceDao
    abstract fun openApiSchemaDao(): OpenApiSchemaDao
    abstract fun resourceCacheDao(): ResourceCacheDao

    companion object {
        const val DATABASE_NAME = "kubenexus.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Preserves existing database structure across schema upgrades
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `api_resources` (
                        `id` TEXT NOT NULL,
                        `clusterId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `singularName` TEXT NOT NULL,
                        `namespaced` INTEGER NOT NULL,
                        `kind` TEXT NOT NULL,
                        `group` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `groupVersion` TEXT NOT NULL,
                        `verbs` TEXT NOT NULL,
                        `shortNames` TEXT NOT NULL,
                        `categories` TEXT NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_api_resources_clusterId` ON `api_resources` (`clusterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_api_resources_clusterId_name` ON `api_resources` (`clusterId`, `name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_api_resources_clusterId_groupVersion_name` ON `api_resources` (`clusterId`, `groupVersion`, `name`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `explained_resources` (
                        `id` TEXT NOT NULL,
                        `clusterId` TEXT NOT NULL,
                        `resourceOrKind` TEXT NOT NULL,
                        `kind` TEXT NOT NULL,
                        `group` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `groupVersion` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `fieldsJson` TEXT NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_explained_resources_clusterId` ON `explained_resources` (`clusterId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_explained_resources_clusterId_resourceOrKind` ON `explained_resources` (`clusterId`, `resourceOrKind`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_explained_resources_clusterId_resourceOrKind_groupVersion` ON `explained_resources` (`clusterId`, `resourceOrKind`, `groupVersion`)")
            }
        }

        /**
         * Replaces the pre-rendered `age` string on `pods` with the pod creation
         * timestamp, so age can be recomputed on read instead of being frozen at
         * sync time.
         *
         * SQLite before 3.35 cannot drop a column, and the minSdk of this app
         * does not guarantee a newer version, so the table is recreated. Cached
         * rows are discarded rather than migrated: the old column held a
         * formatted string such as "2d7h" that cannot be converted back into a
         * timestamp, and the cache is repopulated on the next refresh anyway.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `pods`")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `pods` (
                        `id` TEXT NOT NULL,
                        `clusterId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `namespace` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `readyContainers` TEXT NOT NULL,
                        `restarts` INTEGER NOT NULL,
                        `creationTimestampMillis` INTEGER,
                        `ip` TEXT,
                        `node` TEXT,
                        `image` TEXT,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pods_clusterId_namespace` ON `pods` (`clusterId`, `namespace`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_pods_clusterId_name` ON `pods` (`clusterId`, `name`)")

                // Pod rows were the only cached data keyed off the removed column;
                // force a refresh so the UI does not show an empty list.
                db.execSQL("DELETE FROM `sync_metadata` WHERE `key` LIKE '%_pods'")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `open_api_schemas` (
                        `clusterId` TEXT NOT NULL,
                        `schemaGzip` BLOB NOT NULL,
                        `fetchedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`clusterId`)
                    )
                    """.trimIndent(),
                )
            }
        }

        /**
         * Introduces the offline-first Deployment and Service caches.
         *
         * Both tables are new, so nothing is migrated — only created. The
         * column lists must match what Room derived from the version 7
         * DeploymentEntity and ServiceEntity exactly (types, nullability, order), because
         * Room validates the migrated schema against the exported 7.json.
         * Cached rows start empty and are repopulated by the first sync.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `deployments` (
                        `id` TEXT NOT NULL,
                        `clusterId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `namespace` TEXT NOT NULL,
                        `desiredReplicas` INTEGER NOT NULL,
                        `readyReplicas` INTEGER NOT NULL,
                        `availableReplicas` INTEGER NOT NULL,
                        `updatedReplicas` INTEGER NOT NULL,
                        `creationTimestampMillis` INTEGER,
                        `images` TEXT NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_deployments_clusterId_namespace` " +
                            "ON `deployments` (`clusterId`, `namespace`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_deployments_clusterId_name` " +
                            "ON `deployments` (`clusterId`, `name`)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `services` (
                        `id` TEXT NOT NULL,
                        `clusterId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `namespace` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `clusterIp` TEXT NOT NULL,
                        `ports` TEXT NOT NULL,
                        `creationTimestampMillis` INTEGER,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_services_clusterId_namespace` " +
                            "ON `services` (`clusterId`, `namespace`)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_services_clusterId_name` " +
                            "ON `services` (`clusterId`, `name`)",
                )
            }
        }

        /**
         * Replaces the per-kind `pods`, `deployments` and `services` caches with
         * the single [CachedResourceEntity] table every resource kind shares.
         *
         * Cached rows are discarded rather than converted: they are a cache, the
         * old columns held a different projection, and the first sync of each
         * list repopulates it. Their sync timestamps go too, so no list claims a
         * recent refresh while showing nothing.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `pods`")
                db.execSQL("DROP TABLE IF EXISTS `deployments`")
                db.execSQL("DROP TABLE IF EXISTS `services`")
                db.execSQL("DELETE FROM `sync_metadata` WHERE `resourceType` IN ('pods', 'deployments', 'services')")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_resources` (
                        `id` TEXT NOT NULL,
                        `clusterId` TEXT NOT NULL,
                        `resourceKey` TEXT NOT NULL,
                        `namespace` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `payload` TEXT NOT NULL,
                        `lastUpdated` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_resources_clusterId_resourceKey_namespace` " +
                            "ON `cached_resources` (`clusterId`, `resourceKey`, `namespace`)",
                )
            }
        }

        @Volatile
        private var INSTANCE: KubeNexusDatabase? = null

        fun getInstance(context: Context): KubeNexusDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KubeNexusDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4,
                        MIGRATION_4_5,
                        MIGRATION_5_6,
                        MIGRATION_6_7,
                        MIGRATION_7_8,
                    )
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
