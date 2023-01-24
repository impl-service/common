package cn.subat.impl.common.dto;

import cn.subat.impl.common.config.ImplConfig;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Pageable;
import io.micronaut.serde.annotation.Serdeable;
import io.micronaut.serde.config.naming.SnakeCaseStrategy;
import jakarta.inject.Inject;
import lombok.Data;

import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Data
@Serdeable(naming = SnakeCaseStrategy.class)
public class ImplQuery {

    Integer page;
    Integer perPage = 15;
    Map<String,Object> sort;
    Map<String,Object> filter = new HashMap<>();
    Map<String,Object> like = new HashMap<>();
    Map<String,Object> having = new HashMap<>();

    public Pageable getPageable(){
        return Pageable.from(Math.max(page-1,0),Math.min(perPage > 0 ?perPage:15,100));
    }

    public <T> Specification<T> getQuerySpecification(){
        return (root, query, builder) -> {
            ArrayList<Predicate> predicates = new ArrayList<>();
            //filter
            if(getFilter() != null && getFilter().keySet().size()>0){
                for (String key : getFilter().keySet()) {
                    String value = getFilter().get(key).toString();
                    if(value.equals("")) continue;
                    if(value.charAt(0) == '!'){
                        predicates.add(builder.notEqual(root.get(key),value.substring(1)));
                        continue;
                    }
                    if (value.charAt(0) == '>') {
                        predicates.add(builder.greaterThan(root.get(key),value.substring(1)));
                        continue;
                    }
                    if (value.charAt(0) == '<') {
                        predicates.add(builder.lessThan(root.get(key),value.substring(1)));
                        continue;
                    }
                    predicates.add(builder.equal(root.get(key),getFilter().get(key)));
                }
            }

            //having
            if(getHaving() != null && getHaving().keySet().size()>0){
                for (String key : getHaving().keySet()) {
                    String value = getHaving().get(key).toString();
                    if(value.equals("")) continue;

                    Path<String> path = root.join(key).get("id");

                    if(value.charAt(0) == '!'){
                        predicates.add(builder.notEqual(path,value.substring(1)));
                        continue;
                    }
                    if (value.charAt(0) == '>') {
                        predicates.add(builder.greaterThan(path,value.substring(1)));
                        continue;
                    }
                    if (value.charAt(0) == '<') {
                        predicates.add(builder.lessThan(path,value.substring(1)));
                        continue;
                    }
                    predicates.add(builder.equal(path,getHaving().get(key)));
                }
            }

            //like
            if(getLike() != null && getLike().keySet().size()>0){
                for (String key : getLike().keySet()) {
                    if(getLike().get(key) instanceof String){
                        if (getFilter().get(key).equals("")) {
                            continue;
                        }
                    }
                    predicates.add(builder.like(root.get(key),"%"+getLike().get(key)+"%"));
                }
            }

            //order
            ArrayList<Order> orders = new ArrayList<>();
            if(getSort() != null && getSort().keySet().size()>0){
                for (String key : getSort().keySet()) {
                    if(getSort().get(key).equals("asc")) {
                        orders.add(builder.asc(root.get(key)));
                    }
                    if(getSort().get(key).equals("desc")) {
                        orders.add(builder.desc(root.get(key)));
                    }
                }
            }

            query.orderBy(orders);
            query.where(builder.and(predicates.toArray(new Predicate[0])));
            return query.getRestriction();
        };
    }
}
