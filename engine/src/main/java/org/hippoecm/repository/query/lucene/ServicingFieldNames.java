/*
 *  Copyright 2008 Hippo.
 * 
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.hippoecm.repository.query.lucene;

public interface ServicingFieldNames {
    static final String SVN_ID = "$Id$";
   
    // never used chars in lucene, so can be used as delimiter
    public static final char DATE_RESOLUTION_DELIMITER = '\uFAFA';
    public static final char DATE_NUMBER_DELIMITER = '\uFAFB';
    public static final char LONG_POSTFIX = '\uFAFC';
    public static final char DOUBLE_POSTFIX = '\uFAFD';
    public static final char STRING_DELIMITER = '\uFAFF';
    public static final String STRING_CHAR_POSTFIX = "chars";
    
    /**
     * Lucene field name which is used to index the localname of a node to be able to sort on nodes
     */
    public static final String HIPPO_SORTABLE_NODENAME = "HIPPOSORTABLE:".intern();

    /**
     * Prefix for all field names that are facet properties.
     */
    public static final String HIPPO_FACET = "HIPPOFACET:".intern();

    /**
     * Prefix for all field names that are primary type.
     */
    public static final String HIPPO_PRIMARYTYPE = "_:HIPPO_PT_FACET".intern();

    /**
     * Prefix for all field names that are mixin type.
     */
    public static final String HIPPO_MIXINTYPE = "_:HIPPO_MI_FACET".intern();

    /**
     * Prefix for all field names that are path properties.
     */
    public static final String HIPPO_PATH = "_:HIPPOPATH:".intern();

    /**
     * Prefix for all field names that are depth properties.
     */
    public static final String HIPPO_DEPTH = "_:HIPPODEPTH:".intern();

    /**
     * Name of the field that contains all available properties that are available
     * for this indexed node.
     */
    public static final String FACET_PROPERTIES_SET = "_:FACET_PROPERTIES_SET".intern();
}
