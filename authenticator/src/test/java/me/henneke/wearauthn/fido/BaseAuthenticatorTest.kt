package me.henneke.wearauthn.fido

import android.content.Context
import android.content.SharedPreferences
import io.kotlintest.specs.StringSpec
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.TestCoroutineScope
import me.henneke.wearauthn.fido.context.AuthenticatorContext
import me.henneke.wearauthn.fido.context.AuthenticatorSpecialStatus
import me.henneke.wearauthn.fido.context.AuthenticatorStatus
import me.henneke.wearauthn.fido.context.RequestInfo
import org.mockito.kotlin.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Base test class providing common mocks and utilities for FIDO authenticator testing.
 * 
 * This class sets up:
 * - Mock Android Context and SharedPreferences
 * - Test AuthenticatorContext implementation with stubbed UI functions
 * - Common test utilities and fixtures
 * - Coroutine test scope for async operations
 */
@ExperimentalUnsignedTypes
abstract class BaseAuthenticatorTest : StringSpec({

    // This is the base test class - actual tests are in subclasses

}) {

    protected val testDispatcher = TestCoroutineDispatcher()
    protected val testScope = TestCoroutineScope(testDispatcher)

    // Mock Android components
    protected val mockContext: Context = mock()
    protected val mockSharedPreferences: SharedPreferences = mock()
    protected val mockSharedPreferencesEditor: SharedPreferences.Editor = mock()

    // In-memory storage for SharedPreferences simulation
    private val sharedPrefsStorage = ConcurrentHashMap<String, ConcurrentHashMap<String, Any>>()

    init {
        setupMockContext()
        setupMockSharedPreferences()
    }
    
    private fun setupMockContext() {
        // Mock context to return our mock SharedPreferences
        whenever(mockContext.getSharedPreferences(any(), any())).thenAnswer { invocation ->
            val name = invocation.getArgument<String>(0)
            // Return a mock that uses our in-memory storage
            createMockSharedPreferencesForFile(name)
        }
        
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockContext.packageName).thenReturn("me.henneke.wearauthn.test")
    }
    
    private fun createMockSharedPreferencesForFile(fileName: String): SharedPreferences {
        val storage = sharedPrefsStorage.getOrPut(fileName) { ConcurrentHashMap() }
        val mockPrefs: SharedPreferences = mock()
        val mockEditor: SharedPreferences.Editor = mock()
        
        // Mock SharedPreferences methods
        whenever(mockPrefs.getString(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val defaultValue = invocation.getArgument<String?>(1)
            storage[key] as? String ?: defaultValue
        }
        
        whenever(mockPrefs.getLong(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val defaultValue = invocation.getArgument<Long>(1)
            storage[key] as? Long ?: defaultValue
        }
        
        whenever(mockPrefs.getBoolean(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val defaultValue = invocation.getArgument<Boolean>(1)
            storage[key] as? Boolean ?: defaultValue
        }
        
        whenever(mockPrefs.all).thenReturn(storage.toMap())
        
        // Mock SharedPreferences.Editor methods
        whenever(mockPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<String?>(1)
            if (value != null) storage[key] = value else storage.remove(key)
            mockEditor
        }
        
        whenever(mockEditor.putLong(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<Long>(1)
            storage[key] = value
            mockEditor
        }
        
        whenever(mockEditor.putBoolean(any(), any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            val value = invocation.getArgument<Boolean>(1)
            storage[key] = value
            mockEditor
        }
        
        whenever(mockEditor.remove(any())).thenAnswer { invocation ->
            val key = invocation.getArgument<String>(0)
            storage.remove(key)
            mockEditor
        }
        
        whenever(mockEditor.clear()).thenAnswer {
            storage.clear()
            mockEditor
        }
        
        whenever(mockEditor.commit()).thenReturn(true)
        whenever(mockEditor.apply()).then { /* no-op */ }
        
        return mockPrefs
    }
    
    private fun setupMockSharedPreferences() {
        whenever(mockSharedPreferences.edit()).thenReturn(mockSharedPreferencesEditor)
        whenever(mockSharedPreferencesEditor.putString(any(), any())).thenReturn(mockSharedPreferencesEditor)
        whenever(mockSharedPreferencesEditor.putLong(any(), any())).thenReturn(mockSharedPreferencesEditor)
        whenever(mockSharedPreferencesEditor.putBoolean(any(), any())).thenReturn(mockSharedPreferencesEditor)
        whenever(mockSharedPreferencesEditor.remove(any())).thenReturn(mockSharedPreferencesEditor)
        whenever(mockSharedPreferencesEditor.clear()).thenReturn(mockSharedPreferencesEditor)
        whenever(mockSharedPreferencesEditor.commit()).thenReturn(true)
    }
    
    /**
     * Test implementation of AuthenticatorContext with stubbed UI functions.
     * This allows testing the core business logic without UI dependencies.
     */
    protected inner class TestAuthenticatorContext(
        context: Context = mockContext,
        isHidTransport: Boolean = false
    ) : AuthenticatorContext(context, isHidTransport) {
        
        var lastNotifiedUser: RequestInfo? = null
        var lastSpecialStatus: AuthenticatorSpecialStatus? = null
        private var _confirmRequestResult: Boolean? = true
        private var _confirmTransactionResult: String? = "confirmed"

        val confirmRequestResult: Boolean? get() = _confirmRequestResult
        val confirmTransactionResult: String? get() = _confirmTransactionResult
        
        override fun notifyUser(info: RequestInfo) {
            lastNotifiedUser = info
            // UI functionality stubbed - would show notification to user
        }
        
        override fun handleSpecialStatus(specialStatus: AuthenticatorSpecialStatus) {
            lastSpecialStatus = specialStatus
            // UI functionality stubbed - would handle special status display
        }
        
        override suspend fun confirmRequestWithUser(info: RequestInfo): Boolean? {
            lastNotifiedUser = info
            // UI functionality stubbed - would show confirmation dialog
            return _confirmRequestResult
        }

        override suspend fun confirmTransactionWithUser(rpId: String, prompt: String): String? {
            // UI functionality stubbed - would show transaction confirmation
            return _confirmTransactionResult
        }

        // Helper methods for testing
        fun setConfirmRequestResult(result: Boolean?) {
            _confirmRequestResult = result
        }

        fun setConfirmTransactionResult(result: String?) {
            _confirmTransactionResult = result
        }

        fun clearTestState() {
            lastNotifiedUser = null
            lastSpecialStatus = null
            _confirmRequestResult = true
            _confirmTransactionResult = "confirmed"
        }
    }
    
    /**
     * Creates a test authenticator context with default settings
     */
    protected fun createTestAuthenticatorContext(isHidTransport: Boolean = false): TestAuthenticatorContext {
        return TestAuthenticatorContext(mockContext, isHidTransport)
    }
    
    /**
     * Clears all in-memory SharedPreferences storage
     */
    protected fun clearAllSharedPreferences() {
        sharedPrefsStorage.clear()
    }
    
    /**
     * Gets the current storage state for a specific SharedPreferences file
     */
    protected fun getSharedPreferencesStorage(fileName: String): Map<String, Any> {
        return sharedPrefsStorage[fileName]?.toMap() ?: emptyMap()
    }
    
    /**
     * Common test data and utilities
     */
    companion object {
        // Test RP (Relying Party) data
        const val TEST_RP_ID = "example.com"
        const val TEST_RP_NAME = "Example Corp"
        val TEST_RP_ID_HASH = TEST_RP_ID.toByteArray() // Simplified for testing
        
        // Test user data
        const val TEST_USER_NAME = "testuser@example.com"
        const val TEST_USER_DISPLAY_NAME = "Test User"
        val TEST_USER_ID = "test-user-123".toByteArray()
        
        // Test challenge data
        val TEST_CHALLENGE = ByteArray(32) { it.toByte() }
        val TEST_CLIENT_DATA_HASH = ByteArray(32) { (it + 32).toByte() }
        
        // Test key alias
        const val TEST_KEY_ALIAS = "test-key-alias"
        
        /**
         * Creates a test byte array with specified size and pattern
         */
        fun createTestByteArray(size: Int, pattern: (Int) -> Byte = { it.toByte() }): ByteArray {
            return ByteArray(size, pattern)
        }
        
        /**
         * Creates a hex string from byte array for debugging
         */
        fun ByteArray.toHexString(): String {
            return joinToString("") { "%02x".format(it) }
        }
    }
}
