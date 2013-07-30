/*
 *  Copyright 2008-2013 Hippo B.V. (http://www.onehippo.com)
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
package org.hippoecm.repository;

import java.io.IOException;
import java.util.Random;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.util.OpenBitSet;
import org.junit.Ignore;
import org.junit.Test;

@Ignore
public class DocIdSetTest {

    @Test
    public void testCombineBigDocIdSets() throws IOException {
        Random rand = new Random();
        int nBitsets = 10;
        OpenBitSet[] bitSets = new OpenBitSet[nBitsets];
        for (int i = 0; i < nBitsets; i++) {
            OpenBitSet bitSet = new OpenBitSet();
            for (int j = 0; j < 100 * 1000 * 1000; j++) {
                if (rand.nextInt(i + 1) == 0) {
                    bitSet.set(j);
                }
            }
            bitSets[i] = bitSet;
        }

        {
            long start = System.currentTimeMillis();
            SetDocIdSetBuilder builder = new SetDocIdSetBuilder();
            for (int i = 1; i < 10; i++) {
                builder.add(bitSets[i]);
            }
            final OpenBitSet result = builder.toBitSet();
            System.out.println("docidsetbuilder time: " + (System.currentTimeMillis() - start) + ", cardinality: " + result.cardinality());
        }

        {
            long start = System.currentTimeMillis();
            OpenBitSet bitSet = (OpenBitSet) bitSets[0].clone();
            for (int i = 1; i < 10; i++) {
                OpenBitSet clone = new OpenBitSet();
                final DocIdSetIterator iterator = bitSets[i].iterator();
                while (true) {
                    iterator.nextDoc();
                    int docId = iterator.docID();
                    if (docId == DocIdSetIterator.NO_MORE_DOCS) {
                        break;
                    }
                    clone.set(docId);
                }
                bitSet.and(clone);
            }
            System.out.println("to bitset + bitset#and time: " + (System.currentTimeMillis() - start));
        }

        {
            long start = System.currentTimeMillis();
            OpenBitSet bitSet = (OpenBitSet) bitSets[0].clone();
            for (int i = 1; i < 10; i++) {
                bitSet.and((OpenBitSet) bitSets[i].clone());
            }
            System.out.println("cardinality: " + bitSet.cardinality() + ", pure bitset time: " + (System.currentTimeMillis() - start));
        }
    }
}