package info.fkhodkov.rates.core;

/** Opens a store for one calculation operation. */
@FunctionalInterface
public interface ExchangeRateStoreFactory {
  ExchangeRateStore open() throws Exception;
}
