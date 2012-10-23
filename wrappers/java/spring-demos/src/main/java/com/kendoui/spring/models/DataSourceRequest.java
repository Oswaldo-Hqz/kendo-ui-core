package com.kendoui.spring.models;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.criterion.DetachedCriteria;
import org.hibernate.criterion.Junction;
import org.hibernate.criterion.MatchMode;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.criterion.SimpleExpression;
import org.hibernate.transform.ResultTransformer;

public class DataSourceRequest {
    private int page;
    private int pageSize;
    private int take;
    private int skip;
    private List<SortDescriptor> sort;
    private List<GroupDescriptor> group;
    
    private FilterDescriptor filter;
    
    public DataSourceRequest() {
        filter = new FilterDescriptor();
    }
    
    public int getPage() {
        return page;
    }
    
    public void setPage(int page) {
        this.page = page;
    }
    
    public int getPageSize() {
        return pageSize;
    }
    
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }

    public int getTake() {
        return take;
    }

    public void setTake(int take) {
        this.take = take;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public List<SortDescriptor> getSort() {
        return sort;
    }

    public void setSort(List<SortDescriptor> sort) {
        this.sort = sort;
    }

    public FilterDescriptor getFilter() {
        return filter;
    }

    public void setFilter(FilterDescriptor filter) {
        this.filter = filter;
    }

    private static void restrict(Junction junction, FilterDescriptor filter, Class<?> clazz) {
        String operator = filter.getOperator();
        String field = filter.getField();
        Object value = filter.getValue();
        
        try {
            Class<?> type = new PropertyDescriptor(field, clazz).getPropertyType();
            if (type == double.class || type == Double.class) {
                value = Double.parseDouble(value.toString());
            } else if (type == float.class || type == Float.class) {
                value = Float.parseFloat(value.toString());
            } else if (type == long.class || type == Long.class) {
                value = Long.parseLong(value.toString());
            } else if (type == int.class || type == Integer.class) {
                value = Integer.parseInt(value.toString());
            } else if (type == short.class || type == Short.class) {
                value = Short.parseShort(value.toString());
            } else if (type == boolean.class || type == Boolean.class) {
                value = Boolean.parseBoolean(value.toString());
            }
        }catch (IntrospectionException e) {
        }catch(NumberFormatException nfe) {
        }
        
        switch(operator) {
            case "eq":
                if (value instanceof String) {
                    junction.add(Restrictions.ilike(field, value.toString(), MatchMode.EXACT));
                } else {
                    junction.add(Restrictions.eq(field, value));
                }
                break;
            case "neq":
                if (value instanceof String) {
                    junction.add(Restrictions.not(Restrictions.ilike(field, value.toString(), MatchMode.EXACT)));
                } else {
                    junction.add(Restrictions.ne(field, value));
                }
                break;
            case "gt":
                junction.add(Restrictions.gt(field, value));
                break;
            case "gte":
                junction.add(Restrictions.ge(field, value));
                break;
            case "lt":
                junction.add(Restrictions.lt(field, value));
                break;
            case "lte":
                junction.add(Restrictions.le(field, value));
                break;
            case "startswith":
                junction.add(Restrictions.ilike(field, value.toString(), MatchMode.START));
                break;
            case "endswith":
                junction.add(Restrictions.ilike(field, value.toString(), MatchMode.END));
                break;
            case "contains":
                junction.add(Restrictions.ilike(field, value.toString(), MatchMode.ANYWHERE));
                break;                
            case "doesnotcontain":
                junction.add(Restrictions.not(Restrictions.ilike(field, value.toString(), MatchMode.ANYWHERE)));
                break;
        }

    }
    
    private static void filter(Criteria criteria, FilterDescriptor filter, Class<?> clazz) {
        if (filter != null) {
            List<FilterDescriptor> filters = filter.filters;
            
            if (!filters.isEmpty()) {
                Junction junction = Restrictions.conjunction();
                
                if (!filter.getFilters().isEmpty()  && filter.getLogic().toString().equals("or")) {
                    junction = Restrictions.disjunction();
                }
                
                for(FilterDescriptor entry : filters) {
                    if (!entry.getFilters().isEmpty()) {
                        filter(criteria, entry, clazz);
                    } else {
                        restrict(junction, entry, clazz);
                    }
                }
                
                criteria.add(junction);
            }
        }
    }
    
    private static void sort(Criteria criteria, List<SortDescriptor> sort) {
        if (sort != null && !sort.isEmpty()) {
            for (SortDescriptor entry : sort) {
                String field = entry.getField();
                String dir = entry.getDir();
                
                if (dir.equals("asc")) {
                    criteria.addOrder(Order.asc(field));    
                } else if (dir.equals("desc")) {
                    criteria.addOrder(Order.desc(field));
                }
            }
        }
    }   
    
    private List<?> groupBy(List<?> items, List<GroupDescriptor> group, Class<?> clazz, final Session session, List<SimpleExpression> parentRestrictions)  throws IntrospectionException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
    	ArrayList<Map<String, Object>> result = new ArrayList<Map<String,  Object>>();    	
    	                        
        if (!items.isEmpty() && group != null && !group.isEmpty()) {            
            List<List<SimpleExpression>> restrictions = new ArrayList<List<SimpleExpression>>();            
                    
            GroupDescriptor descriptor = group.get(0);
            List<AggregateDescriptor> aggregates = descriptor.getAggregates();
                    
        	final String field = descriptor.getField();
        	
            Method accessor = new PropertyDescriptor(field, clazz).getReadMethod();          		
            
            Object groupValue = accessor.invoke(items.get(0));
                        
            List<Object> groupItems = createGroupItem(group.size() > 1, clazz, session, result, aggregates, field, groupValue, parentRestrictions);            
            
            List<SimpleExpression> groupRestriction = new ArrayList<SimpleExpression>(parentRestrictions);
            groupRestriction.add(Restrictions.eq(field, groupValue));
            restrictions.add(groupRestriction);
            
            for (Object item : items) {            	
            	Object currentValue = accessor.invoke(item);
            	
				if (!groupValue.equals(currentValue)) {
					groupValue = currentValue;					
					groupItems = createGroupItem(group.size() > 1, clazz, session, result, aggregates, field, groupValue, parentRestrictions);
					
					groupRestriction = new ArrayList<SimpleExpression>(parentRestrictions);
		            groupRestriction.add(Restrictions.eq(field, groupValue));
		            restrictions.add(groupRestriction);
				}
				groupItems.add(item);
			}        
            
            if (group.size() > 1) {   
                Integer counter = 0;
            	for (Map<String,Object> g : result) {            	    
            		g.put("items", groupBy((List<?>)g.get("items"), group.subList(1, group.size()), clazz, session, restrictions.get(counter++)));
				}
            }
        }
        
    	return result;
    }

    @SuppressWarnings("serial")
    private List<Object> createGroupItem(Boolean hasSubgroups, Class<?> clazz, final Session session, ArrayList<Map<String, Object>> result,
            List<AggregateDescriptor> aggregates,
            final String field, 
            Object groupValue,
            List<SimpleExpression> aggregateRestrictions) {
        
        Map<String, Object> groupItem = new HashMap<String, Object>();
        List<Object> groupItems = new ArrayList<Object>();
        
        result.add(groupItem);
        
        groupItem.put("value", groupValue);
        groupItem.put("field", field);
        groupItem.put("hasSubgroups", hasSubgroups);
         
        if (aggregates != null && !aggregates.isEmpty()) {           
            Criteria criteria = session.createCriteria(clazz);
            
            filter(criteria, getFilter(), clazz); // filter the set by the selected criteria            
            
            SimpleExpression currentRestriction = Restrictions.eq(field, groupValue);
            
            if (aggregateRestrictions != null && !aggregateRestrictions.isEmpty()) {
                for (SimpleExpression simpleExpression : aggregateRestrictions) {                    
                    criteria.add(simpleExpression);
                }
            }
            
            groupItem.put("aggregates", criteria
                    .add(currentRestriction)                    
                    .setProjection(createAggregatesProjection(aggregates))                    
                    .setResultTransformer(new ResultTransformer() {                                    
                        @Override
                        public Object transformTuple(Object[] value, String[] aliases) {                            
                            Map<String, Object> result = new HashMap<String, Object>();
                            
                            for (int i = 0; i < aliases.length; i++) {                                
                                String alias = aliases[i];
                                Map<String, Object> aggregate;
                                
                                String name = alias.split("_")[0];
                                if (result.containsKey(name)) {
                                    ((Map<String, Object>)result.get(name)).put(alias.split("_")[1], value[i]);
                                } else {
                                    aggregate = new HashMap<String, Object>();                                    
                                    aggregate.put(alias.split("_")[1], value[i]);        
                                    result.put(name, aggregate);
                                }
                            } 
                            
                            return result;
                        }
                        
                        @Override
                        public List transformList(List collection) {
                            return collection;
                        }
                    })
                    .list()
                    .get(0));
        } else {
            groupItem.put("aggregates", new HashMap<String, Object>());
        }
        groupItem.put("items", groupItems);
        return groupItems;
    }    
    
    private static ProjectionList createAggregatesProjection(List<AggregateDescriptor> aggregates) {
        ProjectionList projections = Projections.projectionList();
        for (AggregateDescriptor aggregate : aggregates) {
            String alias = aggregate.getField() + "_" + aggregate.getAggregate();
            if (aggregate.getAggregate().equals("count")) {                
                projections.add(Projections.count(aggregate.getField()), alias);                
            } else if (aggregate.getAggregate().equals("sum")) {
                projections.add(Projections.sum(aggregate.getField()), alias);                
            } else if (aggregate.getAggregate().equals("average")) {
                projections.add(Projections.avg(aggregate.getField()), alias);                
            } else if (aggregate.getAggregate().equals("min")) {
                projections.add(Projections.min(aggregate.getField()), alias);                
            } else if (aggregate.getAggregate().equals("max")) {
                projections.add(Projections.max(aggregate.getField()), alias);                
            }
        }
        return projections;
    }
    
    private List<?>  group(final Criteria criteria, final List<GroupDescriptor> group, final Session session, final Class<?> clazz) {
    	List<?> result = new ArrayList<Object>();
    	
        if (group != null && !group.isEmpty()) {
            try {
				result = groupBy(criteria.list(), group, clazz, session, new ArrayList<SimpleExpression>());
			} catch (IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | HibernateException
					| IntrospectionException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        }
        return result;
    }

    private static long total(Criteria criteria) {
        long total = (long)criteria.setProjection(Projections.rowCount()).uniqueResult();
        
        criteria.setProjection(null);
        criteria.setResultTransformer(Criteria.ROOT_ENTITY);
        
        return total;
    }
    
    private static void page(Criteria criteria, int take, int skip) {
        criteria.setMaxResults(take);
        criteria.setFirstResult(skip);
    }
    
    public DataSourceResult toDataSourceResult(Session session, Class<?> clazz) {
        Criteria criteria = session.createCriteria(clazz);
        
        filter(criteria, getFilter(), clazz);
        
        long total = total(criteria);
        
        List<SortDescriptor> sort = new ArrayList<SortDescriptor>();
        
        List<GroupDescriptor> groups = getGroup();
        List<SortDescriptor> sorts = getSort();
        
        if (groups != null) {
            sort.addAll(groups);
        }        
        
        if (sorts != null) {
        	sort.addAll(sorts);
        }       
        
        sort(criteria, sort);
    
        page(criteria, getTake(), getSkip());        
        
        DataSourceResult result = new DataSourceResult();
        
        result.setTotal(total);
        
        if (groups != null && !groups.isEmpty()) {        	
			result.setData(group(criteria, groups, session, clazz));									
        } else {
        	result.setData(criteria.list());	
        }
        return result;
    }

    public List<GroupDescriptor> getGroup() {
        return group;
    }

    public void setGroup(List<GroupDescriptor> group) {
        this.group = group;
    }
    
    public static class SortDescriptor {
        private String field;
        private String dir;
        
        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }
    }
    
    public static class GroupDescriptor extends SortDescriptor {        
        private List<AggregateDescriptor> aggregates;

        public GroupDescriptor() {
            aggregates = new ArrayList<AggregateDescriptor>();
        }        
        
        public List<AggregateDescriptor> getAggregates() {
            return aggregates;
        }
    }
    
    public static class AggregateDescriptor {
        private String field;
        private String aggregate;
        
        public String getField() {
            return field;
        }
        public void setField(String field) {
            this.field = field;
        }
        
        public String getAggregate() {
            return aggregate;
        }
        
        public void setAggregate(String aggregate) {
            this.aggregate = aggregate;
        }
    }
    
    public static class FilterDescriptor {
        private String logic;
        private List<FilterDescriptor> filters;        
        private String field;
        private Object value;        
        private String operator;
        
        public FilterDescriptor() {
            filters = new ArrayList<FilterDescriptor>();
        }
        
        public String getField() {
            return field;
        }
        public void setField(String field) {
            this.field = field;
        }
        public Object getValue() {
            return value;
        }
        public void setValue(Object value) {
            this.value = value;
        }
        public String getOperator() {
            return operator;
        }
        public void setOperator(String operator) {
            this.operator = operator;
        }
        
        public String getLogic() {
            return logic;
        }
        
        public void setLogic(String logic) {
            this.logic = logic;
        }

        public List<FilterDescriptor> getFilters() {
            return filters;
        }
    }
}
