package com.youngwoo.jobmoa.shared

open class JobMoaException(message: String) : RuntimeException(message)
class ResourceNotFound(message: String) : JobMoaException(message)
class CollectionConflict(message: String) : JobMoaException(message)
class ServiceUnavailable(message: String) : JobMoaException(message)
class LeaseLost : JobMoaException("작업 소유권이 만료되거나 다른 작업자에게 이전됐습니다.")
