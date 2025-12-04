#!/usr/bin/env python3
"""
Proto file comparison script.
Compares individual schema proto files against the aggregated_models.proto.

Rules:
- Messages that exist in only one file are OK
- If a message exists in both files:
  - All fields must be present in both (missing fields = ERROR)
  - Field types must match (type mismatch = ERROR)
  - Field numbers don't need to match (number mismatch = OK)

- Enums that exist in only one file are OK
- If an enum exists in both files:
  - All enum values must be present in both (missing values = ERROR)
  - Enum value numbers can differ (number mismatch = OK)
"""

import re
from collections import defaultdict
from pathlib import Path
from typing import Dict, Tuple, Set, List, Any

class ProtoParser:
    """Parse protobuf files and extract message/field definitions."""

    def __init__(self, filepath: str):
        self.filepath = filepath
        self.content = Path(filepath).read_text()
        self.messages: Dict[str, Dict] = {}  # message_name -> {fields: {...}, raw: str}
        self.enums: Dict[str, Dict] = {}  # enum_name -> {values: [...]}
        self.parse()

    def parse(self):
        """Parse proto file and extract messages and fields."""
        # Remove comments
        content = re.sub(r'//.*?$', '', self.content, flags=re.MULTILINE)
        content = re.sub(r'/\*.*?\*/', '', content, flags=re.DOTALL)

        # Extract messages
        message_pattern = r'message\s+(\w+)\s*\{(.*?)\n\}'
        for match in re.finditer(message_pattern, content, re.DOTALL):
            msg_name = match.group(1)
            msg_body = match.group(2)
            self.messages[msg_name] = self._parse_message_body(msg_body)
            self.messages[msg_name]['raw'] = match.group(0)

        # Extract enums
        enum_pattern = r'enum\s+(\w+)\s*\{(.*?)\n\}'
        for match in re.finditer(enum_pattern, content, re.DOTALL):
            enum_name = match.group(1)
            enum_body = match.group(2)
            self.enums[enum_name] = self._parse_enum_body(enum_body)

    def _parse_message_body(self, body: str) -> Dict:
        """Parse message body to extract fields."""
        fields = {}
        nested_messages = {}

        # Remove nested messages from body for field parsing
        body_without_nested = re.sub(r'message\s+\w+\s*\{.*?\n\s*\}', '', body, flags=re.DOTALL)
        body_without_nested = re.sub(r'oneof\s+\w+\s*\{.*?\n\s*\}', '', body_without_nested, flags=re.DOTALL)

        # Parse regular fields
        field_pattern = r'(optional|repeated|oneof)?\s*(\w+(?:<\w+,\s*\w+>)?)\s+(\w+)\s*=\s*(\d+)'
        for match in re.finditer(field_pattern, body_without_nested):
            modifier = match.group(1) or ''
            field_type = match.group(2)
            field_name = match.group(3)
            field_number = int(match.group(4))

            fields[field_name] = {
                'type': field_type,
                'number': field_number,
                'modifier': modifier
            }

        # Parse oneof fields
        oneof_pattern = r'oneof\s+(\w+)\s*\{(.*?)\n\s*\}'
        for match in re.finditer(oneof_pattern, body, re.DOTALL):
            oneof_name = match.group(1)
            oneof_body = match.group(2)

            field_pattern_oneof = r'(\w+(?:<\w+,\s*\w+>)?)\s+(\w+)\s*=\s*(\d+)'
            for field_match in re.finditer(field_pattern_oneof, oneof_body):
                field_type = field_match.group(1)
                field_name = field_match.group(2)
                field_number = int(field_match.group(3))

                fields[field_name] = {
                    'type': field_type,
                    'number': field_number,
                    'modifier': 'oneof'
                }

        return {'fields': fields, 'nested': nested_messages}

    def _parse_enum_body(self, body: str) -> Dict:
        """Parse enum body to extract values."""
        values = {}
        # Remove comments
        body = re.sub(r'//.*?$', '', body, flags=re.MULTILINE)

        # Parse enum values: NAME = number;
        value_pattern = r'(\w+)\s*=\s*(\d+)'
        for match in re.finditer(value_pattern, body):
            value_name = match.group(1)
            value_number = int(match.group(2))
            values[value_name] = value_number

        return {'values': values}

    def get_messages(self) -> Dict[str, Dict]:
        """Return all messages."""
        return self.messages

    def get_enums(self) -> Dict[str, Dict]:
        """Return all enums."""
        return self.enums


class ProtoComparator:
    """Compare two proto files."""

    def __init__(self):
        self.errors: Dict[str, List[str]] = defaultdict(list)  # msg_name -> errors
        self.enum_errors: Dict[str, List[str]] = defaultdict(list)  # enum_name -> errors
        self.warnings: List[str] = []
        self.infos: List[str] = []

    def compare_messages(self, msg_name: str, msg1: Dict, msg2: Dict) -> bool:
        """
        Compare two message definitions.
        Returns True if they match (considering the comparison rules).
        """
        fields1 = msg1.get('fields', {})
        fields2 = msg2.get('fields', {})

        is_same = True
        msg_errors = []

        # Check fields in msg1
        for field_name, field_info1 in fields1.items():
            if field_name not in fields2:
                msg_errors.append(
                    f"  - Field '{field_name}' exists in schema but NOT in aggregated"
                )
                is_same = False
            else:
                field_info2 = fields2[field_name]
                # Check type match
                if field_info1['type'] != field_info2['type']:
                    msg_errors.append(
                        f"  - Field '{field_name}' TYPE MISMATCH: "
                        f"Schema={field_info1['type']}, Aggregated={field_info2['type']}"
                    )
                    is_same = False
                # Check modifier match (oneof vs regular)
                if field_info1['modifier'] != field_info2['modifier']:
                    msg_errors.append(
                        f"  - Field '{field_name}' MODIFIER MISMATCH: "
                        f"Schema={field_info1['modifier'] or 'regular'}, "
                        f"Aggregated={field_info2['modifier'] or 'regular'}"
                    )
                    is_same = False

        # Check fields in msg2 that are not in msg1
        for field_name in fields2:
            if field_name not in fields1:
                msg_errors.append(
                    f"  - Field '{field_name}' exists in aggregated but NOT in schema"
                )
                is_same = False

        if msg_errors:
            self.errors[msg_name] = msg_errors

        return is_same

    def compare_enums(self, enum_name: str, enum1: Dict, enum2: Dict) -> bool:
        """
        Compare two enum definitions.
        Returns True if they match.
        Rules:
        - All enum value names must be present in both (missing values = ERROR)
        - Enum value numbers don't need to match (number mismatch = OK)
        """
        values1 = enum1.get('values', {})
        values2 = enum2.get('values', {})

        is_same = True
        enum_errors = []

        # Check values in enum1
        for value_name in values1:
            if value_name not in values2:
                enum_errors.append(
                    f"  - Value '{value_name}' exists in schema but NOT in aggregated"
                )
                is_same = False

        # Check values in enum2 that are not in enum1
        for value_name in values2:
            if value_name not in values1:
                enum_errors.append(
                    f"  - Value '{value_name}' exists in aggregated but NOT in schema"
                )
                is_same = False

        if enum_errors:
            self.enum_errors[enum_name] = enum_errors

        return is_same

    def compare(self, schema_parser: ProtoParser, aggregated_parser: ProtoParser):
        """Compare schema files against aggregated model."""
        schema_messages = schema_parser.get_messages()
        aggregated_messages = aggregated_parser.get_messages()
        schema_enums = schema_parser.get_enums()
        aggregated_enums = aggregated_parser.get_enums()

        compared_messages = set()
        compared_enums = set()

        # Check messages in schema
        for msg_name, msg_schema in schema_messages.items():
            if msg_name in aggregated_messages:
                compared_messages.add(msg_name)
                msg_agg = aggregated_messages[msg_name]
                if self.compare_messages(msg_name, msg_schema, msg_agg):
                    self.infos.append(f"✓ Message '{msg_name}' matches")
            else:
                self.infos.append(f"ℹ Message '{msg_name}' exists only in schema (OK)")

        # Check messages in aggregated not in schema
        for msg_name in aggregated_messages:
            if msg_name not in compared_messages:
                self.infos.append(f"ℹ Message '{msg_name}' exists only in aggregated (OK)")

        # Check enums in schema
        for enum_name, enum_schema in schema_enums.items():
            if enum_name in aggregated_enums:
                compared_enums.add(enum_name)
                enum_agg = aggregated_enums[enum_name]
                if self.compare_enums(enum_name, enum_schema, enum_agg):
                    self.infos.append(f"✓ Enum '{enum_name}' matches")
            else:
                self.infos.append(f"ℹ Enum '{enum_name}' exists only in schema (OK)")

        # Check enums in aggregated not in schema
        for enum_name in aggregated_enums:
            if enum_name not in compared_enums:
                self.infos.append(f"ℹ Enum '{enum_name}' exists only in aggregated (OK)")

    def print_report(self):
        """Print comparison report organized by message and enum."""
        print("\n" + "="*80)
        print("PROTO COMPARISON REPORT - ORGANIZED BY MESSAGE & ENUM")
        print("="*80)

        # Print message mismatches
        if self.errors:
            print(f"\n❌ MESSAGE MISMATCHES ({len(self.errors)} messages):\n")
            for msg_name in sorted(self.errors.keys()):
                print(f"📌 Message: {msg_name}")
                for error in self.errors[msg_name]:
                    print(error)
                print()

        # Print enum mismatches
        if self.enum_errors:
            print(f"\n❌ ENUM MISMATCHES ({len(self.enum_errors)} enums):\n")
            for enum_name in sorted(self.enum_errors.keys()):
                print(f"📌 Enum: {enum_name}")
                for error in self.enum_errors[enum_name]:
                    print(error)
                print()

        if not self.errors and not self.enum_errors:
            print("\n✅ No errors found!")

        if self.warnings:
            print(f"\n⚠️  WARNINGS ({len(self.warnings)}):")
            for warning in self.warnings:
                print(f"  {warning}")

        print(f"\nℹ️  INFO ({len(self.infos)} items):")
        matching = [i for i in self.infos if i.startswith('✓')]
        only_schema = [i for i in self.infos if 'only in schema' in i]
        only_agg = [i for i in self.infos if 'only in aggregated' in i]

        print(f"  ✓ Matching items: {len(matching)}")
        print(f"  ℹ Only in schema: {len(only_schema)}")
        print(f"  ℹ Only in aggregated: {len(only_agg)}")

        print("\n" + "="*80)
        total_issues = len(self.errors) + len(self.enum_errors)
        if total_issues == 0:
            print("✅ COMPARISON PASSED - All messages and enums match!")
        else:
            print(f"❌ COMPARISON FAILED - {total_issues} issue(s) found")
        print("="*80 + "\n")


def main():
    """Main entry point."""
    schema_dir = Path("/home/user/opensearch-protobufs/protos/schemas")
    aggregated_file = Path("/home/user/opensearch-protobufs/protos/generated/models/aggregated_models.proto")

    if not aggregated_file.exists():
        print(f"❌ Aggregated proto file not found: {aggregated_file}")
        return

    # Parse aggregated model
    print(f"📖 Parsing {aggregated_file.name}...")
    aggregated_parser = ProtoParser(str(aggregated_file))
    print(f"   Found {len(aggregated_parser.get_messages())} messages")

    # Parse and compare each schema file
    comparator = ProtoComparator()

    for schema_file in sorted(schema_dir.glob("*.proto")):
        print(f"📖 Parsing {schema_file.name}...")
        schema_parser = ProtoParser(str(schema_file))
        print(f"   Found {len(schema_parser.get_messages())} messages")

        print(f"🔍 Comparing {schema_file.name} against aggregated_models.proto...")
        comparator.compare(schema_parser, aggregated_parser)

    comparator.print_report()

    # Compare default_service.proto with search.proto and document.proto
    print("\n" + "="*80)
    print("COMPARING DEFAULT_SERVICE.PROTO WITH SEARCH.PROTO AND DOCUMENT.PROTO")
    print("="*80)

    default_service_file = Path("/home/user/opensearch-protobufs/protos/generated/services/default_service.proto")
    search_file = Path("/home/user/opensearch-protobufs/protos/schemas/search.proto")
    document_file = Path("/home/user/opensearch-protobufs/protos/schemas/document.proto")

    if default_service_file.exists():
        print(f"\n📖 Parsing {default_service_file.name}...")
        default_service_parser = ProtoParser(str(default_service_file))
        print(f"   Found {len(default_service_parser.get_messages())} messages")

        # Compare with search.proto
        if search_file.exists():
            print(f"📖 Parsing {search_file.name}...")
            search_parser = ProtoParser(str(search_file))
            print(f"   Found {len(search_parser.get_messages())} messages")

            print(f"\n🔍 Comparing {default_service_file.name} vs {search_file.name}...")
            service_search_comparator = ProtoComparator()
            service_search_comparator.compare(default_service_parser, search_parser)
            service_search_comparator.print_report()

        # Compare with document.proto
        if document_file.exists():
            print(f"📖 Parsing {document_file.name}...")
            document_parser = ProtoParser(str(document_file))
            print(f"   Found {len(document_parser.get_messages())} messages")

            print(f"\n🔍 Comparing {default_service_file.name} vs {document_file.name}...")
            service_document_comparator = ProtoComparator()
            service_document_comparator.compare(default_service_parser, document_parser)
            service_document_comparator.print_report()
    else:
        print(f"⚠️  {default_service_file.name} not found")

    # Exit with error code if there are errors
    if comparator.errors or comparator.enum_errors:
        exit(1)




if __name__ == "__main__":
    main()
