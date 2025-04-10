import type {OpenAPIV3} from "openapi-types";
import { traverse } from './utils/OpenApiTraverser';
import isEqual from 'lodash.isequal';


export class SchemaModifier {
    public modify(OpenApiSpec: OpenAPIV3.Document): any {
        traverse(OpenApiSpec, {
            onSchemaProperty: (schema) => {
                this.handleAdditionalPropertiesUndefined(schema)
                this.collapseOrMergeOneOfArray(schema)
            },
            onSchema: (schema, schemaName) => {
                if (!schema || this.isReferenceObject(schema)) return;
                this.handleOneOfConst(schema, schemaName)
                this.collapseOrMergeOneOfArray(schema)
                this.handleAdditionalPropertiesUndefined(schema)
            }
        });
        return OpenApiSpec;
    }

    // Converts `additionalProperties: true` or `additionalProperties: {}` to `type: object`.
    // Example: { additionalProperties: true } -> { type: 'object' }
    handleAdditionalPropertiesUndefined(schema: OpenAPIV3.SchemaObject): void {
        if (schema.additionalProperties === true || ( typeof schema.additionalProperties === 'object' &&  this.isEmptyObjectSchema(schema.additionalProperties as OpenAPIV3.SchemaObject))) {
            schema.type = 'object';
            delete schema.additionalProperties;
        }
    }

    isReferenceObject(schema: any): schema is OpenAPIV3.ReferenceObject {
        return schema !== null && typeof schema === 'object' && '$ref' in schema;
    }

    // Converts `oneOf` schemas with `const` values to boolean types.
    // Example: oneof: [ {type: 'string', const: 'a'} ] to type: 'boolean' with title: 'schemaName_a'
    handleOneOfConst(schema: OpenAPIV3.SchemaObject, schemaName: string): void {
        if (schema.oneOf) {
            for (const item of schema.oneOf) {
                if (item && !('$ref' in item) && item.type === 'string' && 'const' in item) {
                    item.type = 'boolean';
                    item.title = `${schemaName}_${item.const}`;
                    delete item.const;
                }
            }
        }
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

    // Simplify schemas with `oneOf` by aggregating items.
    // If there are only two `oneOf` items and one matches an array schema, remove oneOf type and set type to array.
    // If there are more than two `oneOf` items and one matches an array schema, remove that item from `oneOf`.
    collapseOrMergeOneOfArray(schema: OpenAPIV3.SchemaObject): void{
        if (!('$ref' in schema) && Array.isArray(schema.oneOf)) {
            const oneOfs = schema.oneOf;

            const arraySet = new Set<string>();
            var deleteIndx = -1;

            for (const oneOf of oneOfs) {
                if (this.isArraySchemaObject(oneOf)) {
                    const { type, $ref, additionalProperties} = oneOf.items as any;
                    const oneOfStr = JSON.stringify({ type, $ref, additionalProperties});
                    arraySet.add(oneOfStr)
                }
            }
            for (const oneOf of oneOfs) {
                const { type, $ref, additionalProperties} = oneOf as any;
                const oneOfStr = JSON.stringify({ type, $ref, additionalProperties});
                if (arraySet.has(oneOfStr)) {
                    deleteIndx = oneOfs.findIndex(item => isEqual(item, oneOf));
                    oneOfs.splice(deleteIndx, 1);
                }
            }
            this.collapseSingleItemOneOf(schema);
        }
    }

    collapseSingleItemOneOf(schema: OpenAPIV3.SchemaObject): void {
        if (Array.isArray(schema.oneOf) && schema.oneOf.length === 1) {
            const [singleOneOf] = schema.oneOf as OpenAPIV3.SchemaObject[];
            Object.assign(schema, singleOneOf);
            delete schema.oneOf;
        }
    }

    isArraySchemaObject(schema: any): schema is OpenAPIV3.ArraySchemaObject {
        return (
            typeof schema === 'object' &&
            schema !== null &&
            schema.type === 'array' &&
            'items' in schema
        );
    }
}
