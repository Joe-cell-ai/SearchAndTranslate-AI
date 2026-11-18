import importlib.util
import tempfile
import unittest
import zipfile
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "inspect_realesrgan_onnx.py"
SPEC = importlib.util.spec_from_file_location("inspect_realesrgan_onnx", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ModelInspectionToolTest(unittest.TestCase):
    def test_sha256_is_stable(self):
        self.assertEqual(
            MODULE.sha256(b"abc"),
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
        )

    def test_zip_must_contain_exactly_one_onnx_model(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "models.zip"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("one.onnx", b"model")
                archive.writestr("two.onnx", b"model")
            with self.assertRaisesRegex(ValueError, "exactly one"):
                MODULE.read_model(path)

    def test_reads_single_model_without_extracting_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "model.zip"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("nested/model.onnx", b"model-bytes")
            data, member = MODULE.read_model(path)
            self.assertEqual(data, b"model-bytes")
            self.assertEqual(member, "nested/model.onnx")


if __name__ == "__main__":
    unittest.main()
