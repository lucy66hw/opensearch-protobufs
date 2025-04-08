import type {OpenAPIV3} from "openapi-types";
import { traverse } from './utils/OpenApiTraverser';
export class TypeModifier {
    public modify(OpenApiSpec: OpenAPIV3.Document): any {
        traverse(OpenApiSpec, {
            // change additionalProperties: true or additionalProperties:object to object
            onSchemaProperty: (schema) => {
                if (schema.additionalProperties === true || (
                    typeof schema.additionalProperties === 'object' &&
                    this.isEmptyObjectSchema(schema.additionalProperties as OpenAPIV3.SchemaObject)
                )) {
                    schema.type = 'object';
                    delete schema.additionalProperties;
                }
            },
            onSchema: (schema, schemaName) => {
                // chang const to boolean under oneof.
                // title: oneof_name + const
                if ('oneOf' in schema && Array.isArray(schema.oneOf)) {
                    for (const item of schema.oneOf) {
                        if (
                            item &&
                            !('$ref' in item) &&
                            item.type === 'string' &&
                            this.hasConst(item)
                        ) {
                            (item as any).type = 'boolean';
                            (item as any).title = `${schemaName}_${(item as any).const}`;
                            delete (item as any).const;
                        }
                    }
                }
                // if oneOf has two items, one is a reference and the other is an array schema with a reference in items
                // then convert it to array schema with items as the reference
                if (!('$ref' in schema) && Array.isArray(schema.oneOf) && schema.oneOf.length === 2) {
                    const oneOf = schema.oneOf;

                    let singleRef: string | undefined;
                    let arrayRef: string | undefined;

                    for (const variant of oneOf) {
                        if (this.isReferenceObject(variant)) {
                            singleRef = variant.$ref;
                        } else if (this.isArraySchemaObject(variant) && this.isRefObject(variant.items)) {
                            arrayRef = variant.items.$ref;
                        }
                    }
                    if (oneOf.length === 2 && singleRef && arrayRef && singleRef === arrayRef) {
                        delete schema.oneOf;
                        schema.type = 'array';
                        (schema as OpenAPIV3.ArraySchemaObject).items = { $ref: singleRef };
                    }
                }
            }
        });
        return OpenApiSpec;
    }
    isReferenceObject(schema: any): schema is OpenAPIV3.ReferenceObject {
        return typeof schema === 'object' && schema !== null && '$ref' in schema;
    }
    isRefObject(value: any): value is OpenAPIV3.ReferenceObject {
        return value && typeof value === 'object' && '$ref' in value;
    }
    isArraySchemaObject(
        schema: any
    ): schema is OpenAPIV3.ArraySchemaObject {
        return (
            typeof schema === 'object' &&
            schema !== null &&
            schema.type === 'array' &&
            'items' in schema
        );
    }
    hasConst(schema: unknown): schema is { const: string } {
        return typeof schema === 'object' && schema !== null && 'const' in schema;
    }
    isEmptyObjectSchema(schema: OpenAPIV3.SchemaObject): boolean {
        return (
            schema.type === 'object' &&
            !schema.properties &&
            !schema.allOf &&
            !schema.anyOf &&
            !schema.oneOf &&
            !('$ref' in schema)
        );
    }
}