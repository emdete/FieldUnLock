#!/usr/bin/env python3
from os import listdir, system
from PIL.Image import frombytes, open as fromfile, eval as image_eval, merge as image_merge
from PIL.ImageOps import invert, autocontrast, grayscale, equalize, solarize

def glob(w):
	for n in listdir('.'):
		if n.endswith(w):
			yield n[:-len(w)]

def conv_svg(n):
	print(n)
	for dx, dy, t in (
		# TODO calc sizes
		(400, 400, 'xxh', ),
		(250, 250, 'xh', ),
		(200, 200, 'h', ),
		(150, 150, 'm', ),
	):
		system("echo inkscape -e ../src/main/res/drawable-{}dpi/{}.png -C -w {} -h {} {}.svg".format(
			t, n, dx, dy, n,
			))

def conv_png(n):
	print(n)
	image = fromfile('{}.png'.format(n))
	for dx, dy, t in (
		# TODO calc sizes
		(400, 400, 'xxh', ),
		(250, 250, 'xh', ),
		(200, 200, 'h', ),
		(150, 150, 'm', ),
	):
		image = image.resize((dx, dy, ), )
		image.save('../src/main/res/drawable-{}dpi/{}.png'.format(t, n))
		# TODO pressed
		image.save('../src/main/res/drawable-{}dpi/{}_pressed.png'.format(t, n))

for n in glob('.svg'):
	conv_svg(n)

for n in glob('.png'):
	conv_png(n)
