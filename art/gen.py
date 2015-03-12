#!/usr/bin/env python3
from os import listdir, system
from PIL.Image import frombytes, open as fromfile, eval as image_eval, merge as image_merge
from PIL.ImageOps import invert, autocontrast, grayscale, equalize, solarize

SIZES = (
		(16, 'xxxh', ),
		(12, 'xxh', ),
		(8,  'xh', ),
		(6,  'h', ),
		(4,  'm', ),
		(3,  'l', ),
	)

def glob(w):
	for n in listdir('.'):
		if n.endswith(w):
			yield n[:-len(w)]

def conv_svg(b, n):
	print(n)
	for d, t in SIZES:
		d = int(b * d / 4)
		system("inkscape -e ../src/main/res/drawable-{}dpi/{}.png -C -w {} -h {} {}.svg".format(
			t, n, d, d, n,
			))

def conv_png(b, n):
	print(n)
	image = fromfile('{}.png'.format(n))
	for d, t in SIZES:
		d = int(b * d / 4)
		image = image.resize((d, d, ), )
		image.save('../src/main/res/drawable-{}dpi/{}.png'.format(t, n))
		# TODO pressed
		image.save('../src/main/res/drawable-{}dpi/{}_pressed.png'.format(t, n))

for n in glob('.svg'):
	conv_svg(48, n)

for n in glob('.png'):
	conv_png(150, n)

