/*
 * Copyright 2017 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jbpm.process.core.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.jbpm.util.PatternConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EventTypeFilter implements EventFilter, Serializable {
    private static final Logger logger = LoggerFactory.getLogger(EventTypeFilter.class);

	private static final long serialVersionUID = 510l;
	
	protected String type;

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public boolean acceptsEvent(String type, Object event) {
		if (this.type != null && this.type.equals(type)) {
			return true;
		}
		return false;
	}

	public String toString() { 
	    return "Event filter: [" + this.type + "]";
	}

    @Override
    public boolean acceptsEvent(String type, Object event, Function<String, Object> resolver) {
        return this.type != null && isAccepted(type, resolver);
    }

    public boolean isAccepted(String type, Function<String, Object> resolver) {
        return resolveVariable(this.type, resolver).contains(type);
    }

    private List<String> resolveVariable(String varExpression, Function<String, Object> resolver) {
        if (varExpression == null) {
            return Collections.emptyList();
        }

        Map<String, Object[]> replacements = new HashMap<>();
        Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(varExpression);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = resolver.apply(paramName);
            if (value == null) {
                logger.warn("expression {} in dynamic signal {} not resolved", paramName, varExpression);
               continue;
            } else if (value instanceof Object[]) {
                replacements.put(paramName, (Object[]) value);
            } else {
                replacements.put(paramName, new Object[] {value});
            }
        }

        List<String> acceptedTypes = new ArrayList<>();
        List<Map<String, String>> data = generateCombinations(replacements.keySet(), replacements);

        for(Map<String, String> combination : data) {
            String tmp = varExpression;
            for (Map.Entry<String, String> replacement: combination.entrySet()) {
                tmp = tmp.replace( "#{" + replacement.getKey() + "}", replacement.getValue());
            }
            acceptedTypes.add(tmp);
        }

        if(acceptedTypes.isEmpty()) {
            acceptedTypes.add(varExpression);
        }
        return acceptedTypes;
    }

    private List<Map<String, String>> generateCombinations(Set<String> keys, Map<String, Object[]> data) {
        List<Map<String, String>> combinations = new ArrayList<>();
        for(String key : keys) {
            Set<String> remaining = new HashSet<>(keys);
            remaining.remove(key);
            List<Map<String, String>> subCombinations = generateCombinations(remaining, data);

            if(subCombinations.isEmpty()) {
                for(Object value : data.get(key)) {
                    Map<String, String> combination = new HashMap<>();
                    combination.put(key, value.toString());
                    if (!combinations.contains(combination)) {
                        combinations.add(combination);
                    }
                }
            } else {
                for(Map<String, String> subCombination : subCombinations) {
                    for(Object value : data.get(key)) {
                        Map<String, String> combination = new HashMap<>();
                        combination.putAll(subCombination);
                        combination.put(key, value.toString());
                        if (!combinations.contains(combination)) {
                            combinations.add(combination);
                        }
                    }
                }
            }
        }
        return combinations;
    }
}
