/**
 * Copyright 2021 LinkedIn Corporation. All rights reserved.
 * Licensed under the BSD-2 Clause license.
 * See LICENSE in the project root for license information.
 */
package com.linkedin.coral.schema.avro;

import java.util.List;

import org.apache.hadoop.hive.serde2.typeinfo.ListTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.MapTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.PrimitiveTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.StructTypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.TypeInfo;
import org.apache.hadoop.hive.serde2.typeinfo.UnionTypeInfo;

import com.linkedin.coral.com.google.common.collect.Lists;


/**
 * A Hive {@link TypeInfo} visitor with an accompanying partner schema
 *
 * This visitor traverses the Hive {@link TypeInfo} tree contiguously accessing the schema tree for the partner schema
 * using {@link PartnerAccessor}. When visiting each type in the Hive tree, the implementation is also presented
 * with the corresponding type from the partner schema, or else a {@code null} if no match was found. Matching
 * behavior can be controlled by implementing the methods in {@link PartnerAccessor}
 *
 * @param <P> type of partner schema
 * @param <FP> type of the field representation in the partner schema
 * @param <R> type of the resultant schema generated by the visitor
 * @param <FR> type of the field representation in the resultant schema
 */
@SuppressWarnings("ClassTypeParameterName")
public abstract class HiveSchemaWithPartnerVisitor<P, FP, R, FR> {

  /**
   * Methods to access types in the partner schema corresponding to types in the Hive schema being traversed
   *
   * @param <P> type of partner schema
   * @param <FP> type of the field representation in the partner schema
   */
  public interface PartnerAccessor<P, FP> {

    FP fieldPartner(P partnerStruct, String fieldName);

    P fieldType(FP partnerField);

    P mapKeyPartner(P partnerMap);

    P mapValuePartner(P partnerMap);

    P listElementPartner(P partnerList);

    P unionObjectPartner(P partnerUnion, int ordinal);
  }

  @SuppressWarnings("MethodTypeParameterName")
  public static <P, FP, R, FR> R visit(TypeInfo typeInfo, P partner, HiveSchemaWithPartnerVisitor<P, FP, R, FR> visitor,
      PartnerAccessor<P, FP> accessor) {
    switch (typeInfo.getCategory()) {
      case STRUCT:
        StructTypeInfo structTypeInfo = (StructTypeInfo) typeInfo;
        List<String> names = structTypeInfo.getAllStructFieldNames();
        List<FR> results = Lists.newArrayListWithExpectedSize(names.size());
        for (String name : names) {
          TypeInfo fieldTypeInfo = structTypeInfo.getStructFieldTypeInfo(name);
          FP fieldPartner = partner != null ? accessor.fieldPartner(partner, name) : null;
          P fieldPartnerType = fieldPartner != null ? accessor.fieldType(fieldPartner) : null;
          R result = visit(fieldTypeInfo, fieldPartnerType, visitor, accessor);
          results.add(visitor.field(name, fieldTypeInfo, fieldPartner, result));
        }
        return visitor.struct(structTypeInfo, partner, results);

      case LIST:
        ListTypeInfo listTypeInfo = (ListTypeInfo) typeInfo;
        TypeInfo elementTypeInfo = listTypeInfo.getListElementTypeInfo();
        P elementPartner = partner != null ? accessor.listElementPartner(partner) : null;
        R elementResult = visit(elementTypeInfo, elementPartner, visitor, accessor);
        return visitor.list(listTypeInfo, partner, elementResult);

      case MAP:
        MapTypeInfo mapTypeInfo = (MapTypeInfo) typeInfo;
        P keyPartner = partner != null ? accessor.mapKeyPartner(partner) : null;
        R keyResult = visit(mapTypeInfo.getMapKeyTypeInfo(), keyPartner, visitor, accessor);
        P valuePartner = partner != null ? accessor.mapValuePartner(partner) : null;
        R valueResult = visit(mapTypeInfo.getMapValueTypeInfo(), valuePartner, visitor, accessor);
        return visitor.map(mapTypeInfo, partner, keyResult, valueResult);

      case PRIMITIVE:
        return visitor.primitive((PrimitiveTypeInfo) typeInfo, partner);

      case UNION:
        UnionTypeInfo unionTypeInfo = (UnionTypeInfo) typeInfo;
        List<TypeInfo> allAlternatives = unionTypeInfo.getAllUnionObjectTypeInfos();
        List<R> unionResults = Lists.newArrayListWithExpectedSize(allAlternatives.size());
        for (int i = 0; i < allAlternatives.size(); i++) {
          P unionObjectPartner = partner != null ? accessor.unionObjectPartner(partner, i) : null;
          R result = visit(allAlternatives.get(i), unionObjectPartner, visitor, accessor);
          unionResults.add(result);
        }
        return visitor.union(unionTypeInfo, partner, unionResults);

      default:
        throw new UnsupportedOperationException(typeInfo + " not supported");
    }
  }

  public R struct(StructTypeInfo struct, P partner, List<FR> fieldResults) {
    return null;
  }

  public FR field(String name, TypeInfo field, FP partner, R fieldResult) {
    return null;
  }

  public R list(ListTypeInfo list, P partner, R elementResult) {
    return null;
  }

  public R map(MapTypeInfo map, P partner, R keyResult, R valueResult) {
    return null;
  }

  public R primitive(PrimitiveTypeInfo primitive, P partner) {
    return null;
  }

  public R union(UnionTypeInfo union, P partner, List<R> results) {
    return null;
  }
}
