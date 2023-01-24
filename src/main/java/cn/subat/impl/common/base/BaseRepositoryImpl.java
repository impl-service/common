package cn.subat.impl.common.base;

import cn.subat.impl.common.dto.ImplQuery;
import io.micronaut.data.jpa.repository.JpaSpecificationExecutor;
import io.micronaut.data.repository.PageableRepository;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.transaction.Transactional;
import java.io.Serializable;
import java.util.List;

public abstract class BaseRepositoryImpl<T,ID extends Serializable> implements JpaSpecificationExecutor<T>, PageableRepository<T,ID> {

    Class<T> entityClass;
    @PersistenceContext
    protected EntityManager entityManager;

    public BaseRepositoryImpl(EntityManager entityManager, Class<T> entityClass) {
        this.entityManager = entityManager;
        this.entityClass = entityClass;
    }

    @Transactional
    public List<T> findAll(ImplQuery implQuery) {
        CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
        CriteriaQuery<T> criteriaQuery = criteriaBuilder.createQuery(entityClass);
        Root<T> root = criteriaQuery.from(entityClass);
        Predicate predicate = implQuery.getQuerySpecification().toPredicate((Root<Object>) root, criteriaQuery, criteriaBuilder);
        criteriaQuery.where(predicate);
        int offset = (int) implQuery.getPageable().getOffset();
        int pageSize = implQuery.getPageable().getSize();
        List<T> resultList = entityManager.createQuery(criteriaQuery).setFirstResult(offset).setMaxResults(pageSize).getResultList();
        return resultList;
    }
}
