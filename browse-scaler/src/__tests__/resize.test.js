const fs = require('fs');
const sharp = require('sharp');
const { resizeImage, notFound } = require('../resize');

describe('Image resizing', () => {
  test('Images are resized as expected', async () => {
    const image = fs.readFileSync(`${__dirname}/stars.jpg`);

    const imageResponse = await resizeImage(image, 200, 200);
    fs.writeFileSync(`${__dirname}/stars-resize.png`, imageResponse, {
      encoding: 'base64'
    });

    const imgDimensions = await sharp(`${__dirname}/stars-resize.png`).metadata();
    expect(imgDimensions.width).toBe(200);
    expect(imgDimensions.height).toBe(200);

    fs.unlinkSync(`${__dirname}/stars-resize.png`);
  });

  test('Images aspect ratios are maintained when image sizes do not match', async () => {
    const image = fs.readFileSync(`${__dirname}/desk_flip.jpg`);

    const imageResponse = await resizeImage(image, 458, 358);
    fs.writeFileSync(`${__dirname}/desk-flip-resize.png`, imageResponse, {
      encoding: 'base64'
    });

    const dimensions = await sharp(`${__dirname}/desk-flip-resize.png`).metadata();
    expect(dimensions.width).toBe(358);
    expect(dimensions.height).toBe(358);

    fs.unlinkSync(`${__dirname}/desk-flip-resize.png`);
  });

  test('Resize image to be smaller than original, then larger than original', async () => {
    const image = fs.readFileSync(`${__dirname}/desk_flip.jpg`);

    /* Make Image Smaller */
    const smallerImage = await resizeImage(image, 75, 90);
    fs.writeFileSync(`${__dirname}/desk-flip-smaller.png`, smallerImage, {
      encoding: 'base64'
    });

    const smallerDimensions = await sharp(`${__dirname}/desk-flip-smaller.png`).metadata();
    expect(smallerDimensions.width).toBe(75);
    expect(smallerDimensions.height).toBe(75);

    fs.unlinkSync(`${__dirname}/desk-flip-smaller.png`);

    /* Make Image Larger */
    const largerImage = await resizeImage(image, 600, null);
    fs.writeFileSync(`${__dirname}/desk-flip-larger.png`, largerImage, {
      encoding: 'base64'
    });

    const largerDimensions = await sharp(`${__dirname}/desk-flip-larger.png`).metadata();
    expect(largerDimensions.width).toBe(600);
    expect(largerDimensions.height).toBe(600);

    fs.unlinkSync(`${__dirname}/desk-flip-larger.png`);
  });
});

describe ('notFound', () => {
    test ('returns buffer', async () => {
        const res = await notFound ();
        expect (Buffer.isBuffer (res)).toBeTruthy ();
    });
});
