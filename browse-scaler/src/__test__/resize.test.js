const fs = require("fs");
const sharp = require("sharp");
const { resizeImage, notFound } = require("../resize");

describe("Image resizing", () => {
  test("Images are resized as expected", async () => {
    const image = fs.readFileSync("stars.jpg");

    const imageResponse = await resizeImage(image, 200, 200);
    fs.writeFileSync("stars-resize.png", imageResponse, { encoding: "base64" });

    const imgDimensions = await sharp("stars-resize.png").metadata();
    expect(imgDimensions.width).toBe(200);
    expect(imgDimensions.height).toBe(200);

    fs.unlinkSync("stars-resize.png");
  });

  test("Images aspect ratios are maintained when image sizes do not match", async () => {
    const image = fs.readFileSync("desk_flip.jpg");

    const imageResponse = await resizeImage(image, 458, 358);
    fs.writeFileSync("desk-flip-resize.png", imageResponse, {
      encoding: "base64"
    });

    const dimensions = await sharp("desk-flip-resize.png").metadata();
    expect(dimensions.width).toBe(358);
    expect(dimensions.height).toBe(358);

    fs.unlinkSync("desk-flip-resize.png");
  });

  test("Resize image to be smaller than original, then larger than original", async () => {
    const image = fs.readFileSync("desk_flip.jpg");

    /* Make Image Smaller */
    const smallerImage = await resizeImage(image, 75, 90);
    fs.writeFileSync("desk-flip-smaller.png", smallerImage, {
      encoding: "base64"
    });

    const smallerDimensions = await sharp("desk-flip-smaller.png").metadata();
    expect(smallerDimensions.width).toBe(75);
    expect(smallerDimensions.height).toBe(75);

    fs.unlinkSync("desk-flip-smaller.png");

    /* Make Image Larger */
    const largerImage = await resizeImage(image, 600, null);
    fs.writeFileSync("desk-flip-larger.png", largerImage, {
      encoding: "base64"
    });

    const largerDimensions = await sharp("desk-flip-larger.png").metadata();
    expect(largerDimensions.width).toBe(600);
    expect(largerDimensions.height).toBe(600);

    fs.unlinkSync("desk-flip-larger.png");
  });
});
