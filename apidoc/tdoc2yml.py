#!/usr/bin/env python
# vim: set noexpandtab fileencoding=utf-8 :
"""
Convert old .tdoc docs to new .yml format.
1. Get the jsca by running docgen.py
2. Use the jsca to generate the yaml.
"""

import os, sys, traceback, re
import optparse, yaml, markdown
import docgen
try:
	import json
except:
	import simplejson as json

types = []
doc_dir = os.path.abspath(os.path.dirname(__file__))
OUTPUT_DIR_OVERRIDE = None
skip_methods = (
	'addEventListener',
	'removeEventListener',
	'fireEvent')
skip_methods_view = (
	'add',
	'remove',
	'toImage',
	'animate'
	)
skip_properties = []
skip_properties_view = []
indent = "    "

def clean_type(t):
	if ',' in t:
		t = '[%s]' % t
	if t.lower() == 'array':
		t = 'Array<Object>'
	if t.lower() == 'function':
		t = 'Callback'
	return t

def skip_property(p, t):
	if p['name'] in skip_properties:
		return True
	if t['name'].startswith('Titanium.UI.') and t['name'] != 'Titanium.UI.View':
		return p['name'] in skip_properties_view
	return False

def skip_method(m, t):
	if m['name'] in skip_methods:
		return True
	if t['name'].startswith('Titanium.UI.') and t['name'] != 'Titanium.UI.View':
		return m['name'] in skip_methods_view
	return False

def err(msg, exit=False):
	print >> sys.stderr, "[ERROR] %s" % msg
	if exit:
		sys.exit(1)

def info(msg):
	print "[INFO] %s" % msg

def warn(msg):
	print >> sys.stderr, "[WARN] %s" % msg

def is_module(t):
	name = t['name']
	if name in docgen.apis:
		return (docgen.apis[name].typestr == 'module')
	for tt in types:
		if tt['name'].startswith(t['name']) and tt['name'] != t['name']:
			return True
	return False

def prepare_free_text(s, indent_level=0):
	ind = ''
	for i in range(indent_level + 1):
		ind += indent
	# replace weird backslashes at EOL, which we have in a few TDOCs.
	prog = re.compile(r'\\$', re.M)
	s = prog.sub('', s)
	# `Titanium.XX.XXXX`
	pattern = r'`Ti[^`]*\.[^`]*`'
	matches = re.findall(pattern, s)
	if matches:
		for match in matches:
			s = s.replace(match, '<' + match[1:-1] + '>')
	# [[Titanium.XX.XXXX]]
	pattern = r'\[\[Ti[^\]]*\.[^\]]*\]\]'
	matches = re.findall(pattern, s)
	if matches:
		for match in matches:
			s = s.replace(match, '<' + match[2:-2] + '>')
	# if there are newlines in the TDOC, need to push lines 2-n in by indent
	prog = re.compile(r'\n', re.M)
	if prog.search(s):
		s = prog.sub('\n%s' % ind, s)

	# test it
	y = 'description: %s' % s
	try:
		test = yaml.load(y)
	except:
		# Break the line with YAML-recognized vertical bar, which forces the whole
		# thing to be treated as string and probably gets rid of parse error.
		s = '|\n%s%s' % (ind, s)
	return s


def build_output_path(t):
	path = OUTPUT_DIR_OVERRIDE or doc_dir
	name = t['name']
	qualifier = ".".join(name.split('.')[:-1])
	type_name = name.split('.')[-1]
	if is_module(t):
		path = os.path.join(path, qualifier.replace('.', os.sep), type_name, type_name + '.yml')
	else:
		path = os.path.join(path, qualifier.replace('.', os.sep), type_name + '.yml')
	if not os.path.exists(os.path.dirname(path)):
		os.makedirs(os.path.dirname(path))
	return path

# writeline
def wl(f, line):
	f.write(line + "\n")

def convert_basic_info(t, f):
	pass

# t=type, p=method param, f=file
def convert_parameter(t, m, p, f):
	if 'type' in p and p['type'] in ['int', 'float','double','long']:
		return "\t\t\"methods[name='%s'] parameters[name='%s'] type\": \"%s\",\n" % (m['name'], p['name'], p['type'])
	return ""

# t=type, m=method, f=file
def convert_parameters(t, m, f):
	if not 'parameters' in m:
		return ""
	result = ""
	for p in m['parameters']:
		result += convert_parameter(t, m, p, f)
	return result

# t=type, m=method, f=file
def convert_method(t, m, f):
	result = ""
	if 'returnTypes' in m and m['returnTypes'][0]['type'] in ['int', 'float','double', 'long']:
		result += "\t\t\"methods[name='%s'] returns type\": \"%s\",\n" % (m['name'], m['returnTypes'][0]['type'])
	return result + convert_parameters(t, m, f)

def convert_methods(t, f):
	if not 'functions' in t or len(t['functions']) == 0:
		return ""
	result = ""
	for func in t['functions']:
		if skip_method(func, t):
			continue
		result += convert_method(t, func, f)
	return result

# t=type, e=event, p=event property, f=file
def convert_event_property(t, e, p, f):
	if 'type' in p and p['type'] in ['int', 'float','double','long']:
		return "EVENT!!! \n" % p['name']
	return ""

# t=type, e=event, f=file
def convert_event_properties(t, e, f):
	if not 'properties' in e or len(e['properties']) == 0:
		return ""
	result = ""
	for p in e['properties']:
		result += convert_event_property(t, e, p, f)
	return result

# t=type, e=event, f=file
def convert_event(t, e, f):
	result = ""
	if 'properties' in e:
		result += convert_event_properties(t, e, f)
	return result

def convert_events(t, f):
	if not 'events' in t or len(t['events']) == 0:
		return ""
	result = ""
	for event in t['events']:
		result += convert_event(t, event, f)
	return result

# t=type, p=property, f=file
def convert_property(t, p, f):
	if 'type' in p and p['type'] in ['int', 'float','double', 'long']:
		return "\t\t\"properties[name='%s'] type\": \"%s\",\n" % (p['name'], p['type'])
	return ""

def convert_properties(t, f):
	if not 'properties' in t or len(t['properties']) == 0:
		return ""
	result = ""
	for p in t['properties']:
		if skip_property(p, t):
			continue
		result += convert_property(t, p, f)
	return result

def convert_type(t):
	f = None
	buffer = convert_methods(t, f)
	buffer += convert_events(t, f)
	buffer += convert_properties(t, f)
	if buffer != "":
		print "\t\"%s\": {" % t['name']
		print buffer[:-2]
		print "\t},"

def convert_types():
	print "{"
	for t in types:
		convert_type(t)
	print "}"

def main(args):
	docgen.suppress_htmlerize = True
	docgen.process_tdoc()
	jsca = docgen.produce_jsca({}, False)
	global types
	types = json.loads(jsca)['types']
	convert_types()

if __name__ == "__main__":
	main(sys.argv)

