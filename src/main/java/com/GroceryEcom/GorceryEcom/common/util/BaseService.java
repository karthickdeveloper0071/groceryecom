package com.GroceryEcom.GorceryEcom.common.util;

import java.util.List;
import java.util.Optional;

/**
 * Base Service Interface with common CRUD operations
 */
public interface BaseService<T, ID> {

    T save(T entity);

    Optional<T> findById(ID id);

    List<T> findAll();

    T update(T entity);

    void delete(ID id);

    boolean exists(ID id);

    long count();
}

