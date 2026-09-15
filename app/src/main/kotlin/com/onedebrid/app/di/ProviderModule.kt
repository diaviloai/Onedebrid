@Binds
@Singleton
abstract fun bindDebridProvider(
    impl: RealDebridProvider
): DebridProvider
