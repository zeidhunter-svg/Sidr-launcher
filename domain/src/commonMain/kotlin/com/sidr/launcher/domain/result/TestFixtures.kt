package com.sidr.launcher.domain.result

// Test fixtures for OperationResult. Moved here with the result types (Block A / A5).
// Temporary home in the main source set; relocates to :core:testing in Block B (B4).

fun <T> fakeSuccess(value: T): OperationResult<T> = OperationResult.Success(value)

fun fakeFailure(error: OperationError): OperationResult<Nothing> = OperationResult.Failure(error)
