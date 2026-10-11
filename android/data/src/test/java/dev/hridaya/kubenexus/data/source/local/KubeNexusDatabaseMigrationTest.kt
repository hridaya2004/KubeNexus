package dev.hridaya.kubenexus.data.source.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KubeNexusDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        KubeNexusDatabase::class.java,
    )

    @Test
    fun `version 6 migrates to 8 keeping clusters and dropping the per-kind caches`() {
        helper.createDatabase(DB, 6).use { db ->
            db.execSQL(
                "INSERT INTO clusters (id, name, serverUrl, rawKubeconfig, contextName, userName, namespace, " +
                    "isActive, createdAt, lastConnectedAt, status) " +
                    "VALUES ('c1', 'prod', 'https://prod:6443', 'enc:v1:abc', 'prod', 'admin', 'default', 1, 1, 1, 'CONNECTED')",
            )
            db.execSQL(
                "INSERT INTO sync_metadata (`key`, clusterId, resourceType, lastRefreshedAt) " +
                    "VALUES ('c1_pods', 'c1', 'pods', 5), ('c1_namespaces', 'c1', 'namespaces', 5)",
            )
        }

        helper.runMigrationsAndValidate(DB, 7, true, KubeNexusDatabase.MIGRATION_6_7).close()
        val db = helper.runMigrationsAndValidate(DB, 8, true, KubeNexusDatabase.MIGRATION_7_8)

        db.query("SELECT name, rawKubeconfig FROM clusters WHERE id = 'c1'").use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals("prod", cursor.getString(0))
            assertEquals("enc:v1:abc", cursor.getString(1))
        }
        db.query("SELECT resourceType FROM sync_metadata").use { cursor ->
            val types = buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
            assertFalse("pods" in types)
            assertEquals(listOf("namespaces"), types)
        }
        db.close()
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
