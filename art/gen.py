#!/usr/bin/env python3
from os import listdir
from PIL.Image import frombytes, open as fromfile, eval as image_eval, merge as image_merge
from PIL.ImageOps import invert, autocontrast, grayscale, equalize, solarize

def glob(w):
	for n in listdir('.'):
		if n.endswith(w):
			yield n[:-len(w)]


for n in glob('.png'):
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

