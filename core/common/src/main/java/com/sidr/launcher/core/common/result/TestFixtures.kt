package com.sidr.launcher.core.common.result

fun <T> fakeSuccess(value: T): OperationResult<T> = OperationResult.Success(value)

fun fakeFailure(error: OperationError): OperationResult<Nothing> = OperationResult.Failure(error)
